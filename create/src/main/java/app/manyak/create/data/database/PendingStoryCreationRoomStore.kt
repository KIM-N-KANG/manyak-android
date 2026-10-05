package app.manyak.create.data.database

import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoredCreationDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 간편 제작 편집 초안. 해석할 수 없는 행은 없는 것으로 취급하되 지우지는 않는다.
 *
 * 행은 로그인한 회원의 공개 ID 로 격리한다 — 로그아웃 정리에 지우지 않고, 다른 회원에게는 보이지 않으며,
 * 같은 회원이 돌아오면 그대로 보인다. 초안은 퍼널 세션의 draftId 로 나뉘어 다른 초안을 덮지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PendingStoryCreationRoomStore
    @Inject
    constructor(
        private val dao: PendingStoryCreationDao,
        private val profileRepository: UserProfileRepository,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PendingStoryCreationStore {
        override val drafts: Flow<List<StoredCreationDraft>> =
            profileRepository.profile
                .map { it?.id }
                .flatMapLatest { ownerId ->
                    if (ownerId == null) {
                        flowOf(emptyList())
                    } else {
                        dao.observeAll(ownerId).map { rows -> rows.mapNotNull { it.toStoredDraftOrNull() } }
                    }
                }.flowOn(ioDispatcher)

        override suspend fun claimUnowned() {
            val ownerId = ownerId() ?: return
            withContext(ioDispatcher) { runCatching { dao.claimUnowned(ownerId) } }
        }

        override suspend fun read(draftId: String): PendingStoryCreation? {
            val ownerId = ownerId() ?: return null
            return withContext(ioDispatcher) {
                runCatching { dao.find(draftId, ownerId) }
                    .getOrNull()
                    ?.toDomainOrNull()
            }
        }

        /** 로그인한 회원을 모르면 쓰지 않는다 — 소유자 없는 행은 다음 회원에게 넘어간다. */
        override suspend fun write(
            draftId: String,
            record: PendingStoryCreation,
        ): Boolean {
            val ownerId = ownerId() ?: return false
            val entity = record.toEntity(draftId, ownerId, createdAt = System.currentTimeMillis())
            return withContext(ioDispatcher) { runCatching { dao.save(entity) }.isSuccess }
        }

        override suspend fun clear(draftId: String): Boolean {
            val ownerId = ownerId() ?: return false
            return withContext(ioDispatcher) { runCatching { dao.clear(draftId, ownerId) }.isSuccess }
        }

        private fun ownerId(): String? = profileRepository.profile.value?.id
    }
