package app.manyak.create.data.database

import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.entity.PendingStoryCreation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 간편 제작 편집 슬롯. 해석할 수 없는 행은 없는 것으로 취급하되 지우지는 않는다.
 * 사용자 귀속 데이터이므로 [UserScopedStore] 정리 계약에 참여한다.
 */
@Singleton
class PendingStoryCreationRoomStore
    @Inject
    constructor(
        private val dao: PendingStoryCreationDao,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PendingStoryCreationStore,
        UserScopedStore {
        override val storeName: String = "pending_story_creation"

        override val record: Flow<PendingStoryCreation?> =
            dao
                .observe(PendingStoryCreationEntity.SINGLE_ROW_ID)
                .map { entity -> entity?.toDomainOrNull() }
                .flowOn(ioDispatcher)

        override suspend fun read(): PendingStoryCreation? =
            withContext(ioDispatcher) {
                runCatching { dao.find(PendingStoryCreationEntity.SINGLE_ROW_ID) }
                    .getOrNull()
                    ?.toDomainOrNull()
            }

        override suspend fun write(record: PendingStoryCreation): Boolean =
            withContext(ioDispatcher) {
                runCatching { dao.upsert(record.toEntity()) }.isSuccess
            }

        override suspend fun clear(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dao.clear() }.isSuccess
            }

        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dao.clear() }.isSuccess
            }
    }
