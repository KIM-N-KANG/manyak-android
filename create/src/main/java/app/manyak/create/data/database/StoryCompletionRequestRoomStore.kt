package app.manyak.create.data.database

import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.create.domain.StoryCompletionRequestStore
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.StoryCompletionRequest
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
 * 완성 요청 테이블. 행은 제출한 회원의 공개 ID 로 격리해 로그아웃에 지우지 않는다 — 서버는 요청을
 * 끝까지 완성하므로 같은 회원이 돌아오면 카드가 이어져야 한다. 다른 회원에게는 보이지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class StoryCompletionRequestRoomStore
    @Inject
    constructor(
        private val dao: StoryCompletionRequestDao,
        private val profileRepository: UserProfileRepository,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : StoryCompletionRequestStore {
        override val requests: Flow<List<StoryCompletionRequest>> =
            profileRepository.profile
                .map { it?.id }
                .flatMapLatest { ownerId ->
                    if (ownerId == null) {
                        flowOf(emptyList())
                    } else {
                        dao.observeAll(ownerId).map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
                    }
                }.flowOn(ioDispatcher)

        override suspend fun claimUnowned() {
            val ownerId = ownerId() ?: return
            withContext(ioDispatcher) { runCatching { dao.claimUnowned(ownerId) } }
        }

        override suspend fun readAll(): List<StoryCompletionRequest> {
            val ownerId = ownerId() ?: return emptyList()
            return withContext(ioDispatcher) {
                runCatching { dao.findAll(ownerId) }.getOrDefault(emptyList()).mapNotNull { it.toDomainOrNull() }
            }
        }

        /** 로그인한 회원을 모르면 영속하지 않는다 — 소유자 없는 요청은 다음 회원에게 넘어간다. */
        override suspend fun submit(request: StoryCompletionRequest): Boolean {
            val ownerId = ownerId() ?: return false
            return withContext(ioDispatcher) { runCatching { dao.submit(request.toEntity(ownerId)) }.isSuccess }
        }

        override suspend fun markCompleted(
            requestId: String,
            story: CompletedStory,
        ): Boolean =
            update(
                requestId,
                StoryCompletionRequestEntity.STATUS_COMPLETED,
                storyId = story.id,
                storyTitle = story.title,
            )

        override suspend fun markFailed(requestId: String): Boolean =
            update(requestId, StoryCompletionRequestEntity.STATUS_FAILED, storyId = null, storyTitle = null)

        override suspend fun markPending(requestId: String): Boolean =
            update(requestId, StoryCompletionRequestEntity.STATUS_PENDING, storyId = null, storyTitle = null)

        override suspend fun delete(requestId: String): Boolean =
            withContext(ioDispatcher) { runCatching { dao.delete(requestId) }.isSuccess }

        private fun ownerId(): String? = profileRepository.profile.value?.id

        private suspend fun update(
            requestId: String,
            status: String,
            storyId: String?,
            storyTitle: String?,
        ): Boolean =
            withContext(ioDispatcher) {
                runCatching { dao.updateOutcome(requestId, status, storyId, storyTitle) }.getOrDefault(0) > 0
            }
    }
