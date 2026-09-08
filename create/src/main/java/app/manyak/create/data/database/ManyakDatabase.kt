package app.manyak.create.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 간편 제작 진행 레코드의 로컬 데이터베이스.
 *
 * 편집 슬롯과 완성 요청 두 테이블을 소유한다. 사용자 입력이 든 저장소라 업그레이드는
 * [MIGRATION_1_2] 같은 보존 마이그레이션으로만 하고, 사용자 귀속 테이블은 각자 세션 종료 정리
 * 계약에 참여해야 한다.
 */
@Database(
    entities = [PendingStoryCreationEntity::class, StoryCompletionRequestEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ManyakDatabase : RoomDatabase() {
    abstract fun pendingStoryCreationDao(): PendingStoryCreationDao

    abstract fun storyCompletionRequestDao(): StoryCompletionRequestDao

    companion object {
        const val NAME: String = "manyak.db"
    }
}
