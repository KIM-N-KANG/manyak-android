package app.manyak.create.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryCompletionRequestDao {
    /** 처음 저장 시각 최신순. 시각이 없는 이전 버전 요청은 맨 뒤에서 제출 최신순이다. */
    @Query("SELECT * FROM story_completion_request WHERE ownerId = :ownerId $ORDER_BY_CREATED")
    fun observeAll(ownerId: String): Flow<List<StoryCompletionRequestEntity>>

    @Query("SELECT * FROM story_completion_request WHERE ownerId = :ownerId $ORDER_BY_CREATED")
    suspend fun findAll(ownerId: String): List<StoryCompletionRequestEntity>

    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    @Query("UPDATE story_completion_request SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StoryCompletionRequestEntity)

    @Query("SELECT * FROM pending_story_creation WHERE draftId = :draftId AND ownerId = :ownerId LIMIT 1")
    suspend fun findDraft(
        draftId: String,
        ownerId: String,
    ): PendingStoryCreationEntity?

    /** 제출한 초안만 내린다 — 다른 퍼널 세션의 초안은 건드리지 않는다. */
    @Query("DELETE FROM pending_story_creation WHERE draftId = :draftId AND ownerId = :ownerId")
    suspend fun releaseDraft(
        draftId: String,
        ownerId: String,
    )

    /**
     * 요청 행 삽입과 제출한 초안 해제는 한 트랜잭션이다 — 둘 중 하나만 남으면 초안이 되살아나거나 요청을
     * 잃는다. 처음 임시 저장 시각은 초안에서 이어받아 카드 날짜와 순서가 제출 전후로 같다. 초안이 저장된
     * 적 없으면 이 제출이 처음 저장이다.
     */
    @Transaction
    suspend fun submit(
        entity: StoryCompletionRequestEntity,
        draftId: String,
    ) {
        val draft = findDraft(draftId, entity.ownerId)
        upsert(entity.copy(createdAt = if (draft == null) entity.submittedAt else draft.createdAt))
        releaseDraft(draftId, entity.ownerId)
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

private const val ORDER_BY_CREATED = "ORDER BY createdAt IS NULL, createdAt DESC, submittedAt DESC"
