package app.manyak.create.general.data.database

import android.database.sqlite.SQLiteException
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.create.general.data.GeneralImageFiles
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.entity.GeneralStoredDraft
import app.manyak.create.general.entity.GeneralStoryForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralDraftRoomStore
    @Inject
    constructor(
        private val dao: GeneralDraftDao,
        private val profileRepository: UserProfileRepository,
        private val json: Json,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val files: GeneralImageFiles,
    ) : GeneralDraftStore {
        private val accepted = MutableStateFlow<Map<Pair<String, String>, String>>(emptyMap())

        override val drafts: Flow<List<GeneralStoredDraft>> =
            profileRepository.profile
                .map { it?.id }
                .flatMapLatest { ownerId ->
                    if (ownerId == null) {
                        flowOf(emptyList())
                    } else {
                        combine(dao.observe(ownerId), accepted) { rows, consumed ->
                            rows.filter { (ownerId to it.draftId) !in consumed }.mapNotNull { it.toDomain(json) }
                        }
                    }
                }.flowOn(ioDispatcher)

        override suspend fun read(draftId: String): GeneralStoredDraft? {
            val ownerId = profileRepository.profile.value?.id ?: return null
            if ((ownerId to draftId) in accepted.value) return null
            return withContext(ioDispatcher) {
                val row = dao.find(ownerId, draftId) ?: return@withContext null
                if (row.acceptedSubmissionId != null) return@withContext null
                checkNotNull(row.toDomain(json)) { "저장한 초안을 복원할 수 없습니다." }
            }
        }

        override suspend fun acceptedSubmissionId(draftId: String): String? {
            val ownerId = profileRepository.profile.value?.id ?: return null
            accepted.value[ownerId to draftId]?.let { return it }
            return withContext(ioDispatcher) { dao.find(ownerId, draftId)?.acceptedSubmissionId }
        }

        override suspend fun save(
            draftId: String,
            form: GeneralStoryForm,
        ): Boolean {
            val ownerId = profileRepository.profile.value?.id ?: return false
            if ((ownerId to draftId) in accepted.value) return false
            return storedOrNull {
                val row = dao.find(ownerId, draftId)
                if (row?.acceptedSubmissionId != null) return@storedOrNull false
                val previous = row?.toDomain(json)?.form
                if (!form.hasInput) {
                    dao.deleteEditable(ownerId, draftId)
                    files.removeUnused(ownerId, previous)
                    true
                } else {
                    val saved =
                        dao.save(
                            GeneralDraftEntity(
                                ownerId,
                                draftId,
                                json.encodeToString(GeneralDraftSnapshot(form = form)),
                                System.currentTimeMillis(),
                            ),
                        )
                    if (saved) files.removeUnused(ownerId, previous, form)
                    saved
                }
            } ?: false
        }

        override suspend fun markAccepted(
            draftId: String,
            submissionId: String,
        ): Boolean {
            val ownerId = profileRepository.profile.value?.id ?: return false
            // 응답은 받았지만 DB 기록이 실패한 동안에도 같은 프로세스에서 재제출되지 않게 한다.
            accepted.update { it + ((ownerId to draftId) to submissionId) }
            return storedOrNull {
                dao.accept(ownerId, draftId, submissionId, System.currentTimeMillis())
                true
            } ?: false
        }

        override suspend fun delete(draftId: String): Boolean {
            val ownerId = profileRepository.profile.value?.id ?: return false
            return storedOrNull {
                val previous = dao.find(ownerId, draftId)?.toDomain(json)?.form
                val deleted = dao.deleteEditable(ownerId, draftId) > 0
                if (deleted) files.removeUnused(ownerId, previous)
                deleted
            } ?: false
        }

        private suspend fun <T> storedOrNull(block: suspend () -> T): T? =
            withContext(ioDispatcher) {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SQLiteException) {
                    null
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalStateException) {
                    null
                }
            }
    }
