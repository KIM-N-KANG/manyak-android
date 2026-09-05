package app.manyak.create.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 간편 제작 진행 레코드의 로컬 데이터베이스.
 *
 * 제작 진행 레코드 한 테이블을 소유한다.
 * 사용자 귀속 테이블은 각자 세션 종료 정리 계약에 참여해야 한다.
 */
@Database(
    entities = [PendingStoryCreationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ManyakDatabase : RoomDatabase() {
    abstract fun pendingStoryCreationDao(): PendingStoryCreationDao

    companion object {
        const val NAME: String = "manyak.db"
    }
}
