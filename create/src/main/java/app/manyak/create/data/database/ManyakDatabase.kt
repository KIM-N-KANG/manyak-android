package app.manyak.create.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 간편 제작 진행 레코드의 로컬 데이터베이스.
 *
 * 편집 슬롯과 완성 요청 두 테이블을 소유한다. 사용자 입력이 든 저장소라 업그레이드는
 * [MIGRATION_1_2] 같은 보존 마이그레이션으로만 한다. 두 테이블은 로그아웃에 지우지 않고 행마다
 * 소유 회원 ID 로 격리한다 — 완성 중 로그아웃했다가 같은 계정으로 돌아오면 요청이 이어져야 한다.
 */
@Database(
    entities = [PendingStoryCreationEntity::class, StoryCompletionRequestEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ManyakDatabase : RoomDatabase() {
    abstract fun pendingStoryCreationDao(): PendingStoryCreationDao

    abstract fun storyCompletionRequestDao(): StoryCompletionRequestDao

    companion object {
        const val NAME: String = "manyak.db"
    }
}
