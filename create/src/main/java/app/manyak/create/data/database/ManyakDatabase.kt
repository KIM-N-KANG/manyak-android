package app.manyak.create.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.manyak.create.general.data.database.GeneralDraftDao
import app.manyak.create.general.data.database.GeneralDraftEntity

/**
 * 제작 초안과 간편 제작 진행 레코드의 로컬 데이터베이스.
 *
 * 간편 제작 초안, 완성 요청, 일반 제작 초안을 소유한다. 사용자 입력이 든 저장소라 업그레이드는
 * [MIGRATION_1_2] 같은 보존 마이그레이션으로만 한다. 로그아웃에 지우지 않고 행마다
 * 소유 회원 ID로 격리해 같은 계정으로 돌아오면 작업을 이어간다.
 */
@Database(
    entities = [PendingStoryCreationEntity::class, StoryCompletionRequestEntity::class, GeneralDraftEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class ManyakDatabase : RoomDatabase() {
    abstract fun pendingStoryCreationDao(): PendingStoryCreationDao

    abstract fun storyCompletionRequestDao(): StoryCompletionRequestDao

    abstract fun generalDraftDao(): GeneralDraftDao

    companion object {
        const val NAME: String = "manyak.db"
    }
}
