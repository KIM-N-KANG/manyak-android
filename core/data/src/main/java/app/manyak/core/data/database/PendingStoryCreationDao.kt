package app.manyak.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingStoryCreationDao {
    @Query("SELECT * FROM pending_story_creation WHERE id = :id LIMIT 1")
    fun observe(id: Int): Flow<PendingStoryCreationEntity?>

    @Query("SELECT * FROM pending_story_creation WHERE id = :id LIMIT 1")
    suspend fun find(id: Int): PendingStoryCreationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingStoryCreationEntity)

    @Query("DELETE FROM pending_story_creation")
    suspend fun clear()
}
