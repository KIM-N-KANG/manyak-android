package app.manyak.create.general.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneralDraftDao {
    @Query(
        "SELECT * FROM general_story_draft WHERE ownerId = :ownerId AND acceptedSubmissionId IS NULL " +
            "ORDER BY createdAt DESC",
    )
    fun observe(ownerId: String): Flow<List<GeneralDraftEntity>>

    @Query("SELECT * FROM general_story_draft WHERE ownerId = :ownerId AND draftId = :draftId LIMIT 1")
    suspend fun find(
        ownerId: String,
        draftId: String,
    ): GeneralDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GeneralDraftEntity)

    @Transaction
    suspend fun save(entity: GeneralDraftEntity): Boolean {
        val previous = find(entity.ownerId, entity.draftId)
        if (previous?.acceptedSubmissionId != null) return false
        upsert(entity.copy(createdAt = previous?.createdAt ?: entity.createdAt))
        return true
    }

    @Transaction
    suspend fun accept(
        ownerId: String,
        draftId: String,
        submissionId: String,
        now: Long,
    ) {
        val previous = find(ownerId, draftId)
        upsert(GeneralDraftEntity(ownerId, draftId, null, previous?.createdAt ?: now, submissionId))
    }

    @Query("DELETE FROM general_story_draft WHERE ownerId = :ownerId AND draftId = :draftId")
    suspend fun delete(
        ownerId: String,
        draftId: String,
    ): Int

    @Query(
        "DELETE FROM general_story_draft WHERE ownerId = :ownerId AND draftId = :draftId " +
            "AND acceptedSubmissionId IS NULL",
    )
    suspend fun deleteEditable(
        ownerId: String,
        draftId: String,
    ): Int
}
