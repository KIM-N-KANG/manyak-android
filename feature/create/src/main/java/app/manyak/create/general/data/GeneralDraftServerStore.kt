package app.manyak.create.general.data

import android.database.sqlite.SQLiteException
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.create.data.api.StoryDraftCardDto
import app.manyak.create.data.api.StoryDraftDetailDto
import app.manyak.create.data.draft.StoryDraftRemote
import app.manyak.create.data.draft.isNotFound
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.general.data.api.GENERAL_DRAFT_SCHEMA_VERSION
import app.manyak.create.general.data.api.GENERAL_MODE
import app.manyak.create.general.data.api.draftGenreTagIds
import app.manyak.create.general.data.api.toDraftRequest
import app.manyak.create.general.data.api.toGeneralStoryForm
import app.manyak.create.general.data.database.GeneralDraftDao
import app.manyak.create.general.data.database.toForm
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.entity.GeneralDraftOpen
import app.manyak.create.general.entity.GeneralStoredDraft
import app.manyak.create.general.entity.GeneralStoryForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 일반 제작 임시 저장본을 서버 초안으로 둔다. Room에는 서버로 옮기기 전의 저장본과, 접수한 초안을 제출본에 잇는
 * 연결 행만 남는다. 서버 목록은 계정별로 마지막에 받은 것을 기억해 다시 받다 실패해도 카드가 사라지지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralDraftServerStore
    @Inject
    constructor(
        private val remote: StoryDraftRemote,
        private val dao: GeneralDraftDao,
        private val profileRepository: UserProfileRepository,
        private val catalog: StoryCreationRepository,
        private val json: Json,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val files: GeneralImageFiles,
    ) : GeneralDraftStore {
        private val accepted = MutableStateFlow<Map<Pair<String, String>, String>>(emptyMap())
        private val refreshing = Mutex()

        private val ownerId: String? get() = profileRepository.profile.value?.id

        override val drafts: Flow<List<GeneralStoredDraft>> =
            profileRepository.profile
                .map { it?.id }
                .flatMapLatest { ownerId ->
                    if (ownerId == null) {
                        flowOf(emptyList())
                    } else {
                        combine(dao.observe(ownerId), accepted, remote.cards(ownerId)) { rows, consumed, cards ->
                            val server = cards.mapNotNull { it.toGeneralDraft() }
                            val serverIds = server.mapTo(mutableSetOf()) { it.draftId }
                            val local =
                                rows.filter { it.draftId !in serverIds }.mapNotNull { row ->
                                    row.toForm(json)?.let { form ->
                                        GeneralStoredDraft(
                                            row.draftId,
                                            row.createdAt,
                                            form.title,
                                            form.oneLineIntro,
                                            form.cover?.previewUrl,
                                        )
                                    }
                                }
                            (server + local).filter { (ownerId to it.draftId) !in consumed }
                        }
                    }
                }.flowOn(ioDispatcher)

        override suspend fun refresh() {
            val ownerId = ownerId ?: return
            refreshing.withLock {
                if (!remote.refresh(ownerId)) return
                val legacy = stored { dao.observe(ownerId).first() }.orEmpty()
                val moved = mutableListOf<Pair<String, GeneralStoryForm>>()
                for ((draftId, form) in legacy.mapNotNull { row -> row.toForm(json)?.let { row.draftId to it } }) {
                    when (put(draftId, form, null)) {
                        // 409는 같은 ID가 이미 서버에 있다는 뜻이라 기기 안 사본은 더 둘 이유가 없다.
                        is StoryDraftSave.Saved, is StoryDraftSave.Conflict -> moved += draftId to form
                        StoryDraftSave.LimitExceeded -> break
                        StoryDraftSave.TooLarge, StoryDraftSave.Failed -> Unit
                    }
                }
                // 서버 카드를 받은 뒤에 지워 카드가 잠깐 사라지지 않게 한다. 못 받으면 다음에 같은 내용을 다시 보내 지운다.
                if (moved.isNotEmpty() && remote.refresh(ownerId)) {
                    moved.forEach { (draftId, form) -> stored { deleteLocal(ownerId, draftId, form) } }
                }
            }
        }

        override suspend fun open(draftId: String): DomainResult<GeneralDraftOpen> {
            val local = ownerId?.let { owner -> stored { dao.find(owner, draftId) } }
            if (local != null && local.acceptedSubmissionId == null) {
                return local.toForm(json)?.let { DomainResult.Success(GeneralDraftOpen.Local(it)) }
                    ?: DomainResult.Failure(DomainError.Serialization)
            }
            return when (val result = remote.detail(draftId)) {
                is DomainResult.Failure ->
                    if (result.error.isNotFound()) DomainResult.Success(GeneralDraftOpen.NotFound) else result
                is DomainResult.Success -> result.value.toOpen()
            }
        }

        override suspend fun acceptedSubmissionId(draftId: String): String? {
            val ownerId = ownerId ?: return null
            accepted.value[ownerId to draftId]?.let { return it }
            return withContext(ioDispatcher) { dao.find(ownerId, draftId)?.acceptedSubmissionId }
        }

        override suspend fun save(
            draftId: String,
            form: GeneralStoryForm,
            baseVersion: Int?,
        ): StoryDraftSave {
            val ownerId = ownerId ?: return StoryDraftSave.Failed
            if (acceptedSubmissionId(draftId) != null) return StoryDraftSave.Failed
            val result = put(draftId, form, baseVersion)
            // 이어서 연 기기 안 저장본은 서버로 옮겨졌다. 이미지 파일은 화면이 아직 미리보기로 쓰고 있어 화면 수명이 정리한다.
            if (result is StoryDraftSave.Saved) stored { dao.deleteEditable(ownerId, draftId) }
            return result
        }

        override suspend fun markAccepted(
            draftId: String,
            submissionId: String,
        ): Boolean {
            val ownerId = ownerId ?: return false
            // 응답은 받았지만 DB 기록이 실패한 동안에도 같은 프로세스에서 재제출되지 않게 한다.
            accepted.update { it + ((ownerId to draftId) to submissionId) }
            return stored {
                dao.accept(ownerId, draftId, submissionId, System.currentTimeMillis())
                true
            } ?: false
        }

        override suspend fun delete(draftId: String): Boolean {
            val ownerId = ownerId ?: return false
            if (!remote.delete(draftId)) return false
            return stored {
                val form = dao.find(ownerId, draftId)?.toForm(json)
                deleteLocal(ownerId, draftId, form)
            } != null
        }

        private suspend fun put(
            draftId: String,
            form: GeneralStoryForm,
            baseVersion: Int?,
        ): StoryDraftSave = remote.save(draftId, baseVersion) { form.toDraftRequest(it) }

        private suspend fun StoryDraftDetailDto.toOpen(): DomainResult<GeneralDraftOpen> {
            // 모르는 스키마 버전을 읽어 다시 저장하면 그 버전에만 있는 필드가 지워진다.
            if (mode != GENERAL_MODE || schemaVersion != GENERAL_DRAFT_SCHEMA_VERSION) {
                return DomainResult.Failure(DomainError.Serialization)
            }
            val genreNames =
                if (payload.draftGenreTagIds().isEmpty()) {
                    emptyMap()
                } else {
                    // 장르 이름을 찾지 못한 채 열면 다음 저장이 장르를 지운다.
                    when (val genres = catalog.genres()) {
                        is DomainResult.Failure -> return genres
                        is DomainResult.Success -> genres.value.genres.associate { it.id to it.name }
                    }
                }
            return DomainResult.Success(
                GeneralDraftOpen.Server(payload.toGeneralStoryForm(imageUrls, genreNames), version),
            )
        }

        private suspend fun deleteLocal(
            ownerId: String,
            draftId: String,
            form: GeneralStoryForm?,
        ) {
            if (dao.deleteEditable(ownerId, draftId) > 0) files.removeUnused(ownerId, form)
        }

        private suspend fun <T> stored(block: suspend () -> T): T? =
            withContext(ioDispatcher) {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SQLiteException) {
                    null
                } catch (_: IllegalStateException) {
                    null
                }
            }
    }

private fun StoryDraftCardDto.toGeneralDraft(): GeneralStoredDraft? =
    draftId?.takeIf { kind == "DRAFT" && mode == GENERAL_MODE }?.let {
        GeneralStoredDraft(it, createdAt?.toEpochMillisOrNull(), title, oneLineIntro, thumbnailUrl)
    }
