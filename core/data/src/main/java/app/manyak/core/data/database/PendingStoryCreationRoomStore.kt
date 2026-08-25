package app.manyak.core.data.database

import app.manyak.core.data.di.IoDispatcher
import app.manyak.core.data.session.UserScopedStore
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 간편 제작 진행 레코드의 단일 슬롯.
 *
 * 해석할 수 없는 행은 없는 것으로 취급한다 — 재생성 가능한 진행 스냅숏이라 복구보다 폐기가
 * 안전하다. 사용자 귀속 데이터이므로 [UserScopedStore] 정리 계약에 참여한다.
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

        override suspend fun write(record: PendingStoryCreation) {
            withContext(ioDispatcher) {
                runCatching { dao.upsert(record.toEntity()) }
            }
        }

        override suspend fun clear() {
            withContext(ioDispatcher) {
                runCatching { dao.clear() }
            }
        }

        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dao.clear() }.isSuccess
            }
    }
