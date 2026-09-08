package app.manyak.create.data.database

import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.create.domain.StoryCompletionRequestStore
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.StoryCompletionRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 완성 요청 테이블. 사용자 귀속 데이터이므로 [UserScopedStore] 정리 계약에 참여한다. */
@Singleton
class StoryCompletionRequestRoomStore
    @Inject
    constructor(
        private val dao: StoryCompletionRequestDao,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : StoryCompletionRequestStore,
        UserScopedStore {
        override val storeName: String = "story_completion_request"

        override val requests: Flow<List<StoryCompletionRequest>> =
            dao
                .observeAll()
                .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
                .flowOn(ioDispatcher)

        override suspend fun readAll(): List<StoryCompletionRequest> =
            withContext(ioDispatcher) {
                runCatching { dao.findAll() }.getOrDefault(emptyList()).mapNotNull { it.toDomainOrNull() }
            }

        override suspend fun submit(request: StoryCompletionRequest): Boolean =
            withContext(ioDispatcher) { runCatching { dao.submit(request.toEntity()) }.isSuccess }

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

        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) { runCatching { dao.clear() }.isSuccess }

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
