package app.manyak.create.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryCompletionRequestDao {
    @Query("SELECT * FROM story_completion_request WHERE ownerId = :ownerId ORDER BY submittedAt DESC")
    fun observeAll(ownerId: String): Flow<List<StoryCompletionRequestEntity>>

    @Query("SELECT * FROM story_completion_request WHERE ownerId = :ownerId ORDER BY submittedAt DESC")
    suspend fun findAll(ownerId: String): List<StoryCompletionRequestEntity>

    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    @Query("UPDATE story_completion_request SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StoryCompletionRequestEntity)

    /**
     * 제출한 초안만 슬롯에서 내린다 — 같은 생성 결과를 담은 행이 아니면 다른 초안이므로 건드리지 않는다.
     */
    @Query(
        "DELETE FROM pending_story_creation WHERE id = :draftId AND ownerId = :ownerId AND generation = :generation",
    )
    suspend fun releaseDraft(
        draftId: Int,
        ownerId: String,
        generation: String,
    )

    /** 요청 행 삽입과 해당 초안 해제는 한 트랜잭션이다 — 둘 중 하나만 남으면 초안이 되살아나거나 요청을 잃는다. */
    @Transaction
    suspend fun submit(entity: StoryCompletionRequestEntity) {
        releaseDraft(PendingStoryCreationEntity.SINGLE_ROW_ID, entity.ownerId, entity.generation)
        upsert(entity)
    }

    @Query(
        "UPDATE story_completion_request SET status = :status, storyId = :storyId, storyTitle = :storyTitle " +
            "WHERE requestId = :requestId",
    )
    suspend fun updateOutcome(
        requestId: String,
        status: String,
        storyId: String?,
        storyTitle: String?,
    ): Int

    @Query("DELETE FROM story_completion_request WHERE requestId = :requestId")
    suspend fun delete(requestId: String)
}
