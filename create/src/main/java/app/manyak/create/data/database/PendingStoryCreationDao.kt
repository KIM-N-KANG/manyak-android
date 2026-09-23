package app.manyak.create.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingStoryCreationDao {
    /** 처음 저장 시각 최신순. 시각이 없는 이전 버전 행은 맨 뒤다. */
    @Query(
        "SELECT * FROM pending_story_creation WHERE ownerId = :ownerId ORDER BY createdAt IS NULL, createdAt DESC",
    )
    fun observeAll(ownerId: String): Flow<List<PendingStoryCreationEntity>>

    @Query("SELECT * FROM pending_story_creation WHERE draftId = :draftId AND ownerId = :ownerId LIMIT 1")
    suspend fun find(
        draftId: String,
        ownerId: String,
    ): PendingStoryCreationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingStoryCreationEntity)

    /**
     * 초안을 쓴다. 처음 저장 시각은 행이 처음 생길 때만 정한다 — 단계가 바뀌어도 카드 순서와 날짜가
     * 흔들리지 않고, 시각 없이 남은 이전 버전 행도 그대로 시각 없이 둔다.
     */
    @Transaction
    suspend fun save(entity: PendingStoryCreationEntity) {
        val existing = find(entity.draftId, entity.ownerId)
        upsert(if (existing == null) entity else entity.copy(createdAt = existing.createdAt))
    }

    @Query("DELETE FROM pending_story_creation WHERE draftId = :draftId AND ownerId = :ownerId")
    suspend fun clear(
        draftId: String,
        ownerId: String,
    )

    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    @Query("UPDATE pending_story_creation SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String)
}
