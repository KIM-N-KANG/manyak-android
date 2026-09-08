package app.manyak.create.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingStoryCreationDao {
    @Query("SELECT * FROM pending_story_creation WHERE id = :id AND ownerId = :ownerId LIMIT 1")
    fun observe(
        id: Int,
        ownerId: String,
    ): Flow<PendingStoryCreationEntity?>

    @Query("SELECT * FROM pending_story_creation WHERE id = :id AND ownerId = :ownerId LIMIT 1")
    suspend fun find(
        id: Int,
        ownerId: String,
    ): PendingStoryCreationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingStoryCreationEntity)

    @Query("DELETE FROM pending_story_creation WHERE id = :id AND ownerId = :ownerId")
    suspend fun clear(
        id: Int,
        ownerId: String,
    )

    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    @Query("UPDATE pending_story_creation SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String)
}
