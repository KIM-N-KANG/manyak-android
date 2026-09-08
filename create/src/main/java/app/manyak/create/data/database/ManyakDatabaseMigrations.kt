package app.manyak.create.data.database

import androidx.core.database.getStringOrNull
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 단일 슬롯의 `STORY_COMPLETION` 행을 완성 요청 테이블로 옮긴다.
 *
 * 사용자 입력과 복구 키(requestId)가 든 저장소라 파괴적 재생성을 쓰지 않는다. 옮길 수 있는 행만
 * 옮기고 원본을 지우며, 해석할 수 없는 행은 원본 그대로 두어 이후 검토할 수 있게 남긴다.
 */
internal val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `story_completion_request` (" +
                    "`requestId` TEXT NOT NULL, `completionCommand` TEXT NOT NULL, `generationCommand` TEXT, " +
                    "`generation` TEXT NOT NULL, `progress` TEXT NOT NULL, `submittedAt` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `storyId` TEXT, `storyTitle` TEXT, PRIMARY KEY(`requestId`))",
            )
            val moved = mutableListOf<Int>()
            db
                .query(
                    "SELECT id, generationCommand, completionCommand, generation, progress " +
                        "FROM pending_story_creation WHERE stage = '$STAGE_STORY_COMPLETION'",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val request =
                            legacyCompletionToRequest(
                                generationCommand = cursor.getStringOrNull(1),
                                completionCommand = cursor.getStringOrNull(2),
                                generation = cursor.getStringOrNull(3),
                                progress = cursor.getStringOrNull(4),
                                submittedAt = System.currentTimeMillis(),
                            ) ?: continue
                        db.execSQL(
                            "INSERT OR REPLACE INTO story_completion_request " +
                                "(requestId, completionCommand, generationCommand, generation, progress, " +
                                "submittedAt, status, storyId, storyTitle) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
                            arrayOf<Any?>(
                                request.requestId,
                                request.completionCommand,
                                request.generationCommand,
                                request.generation,
                                request.progress,
                                request.submittedAt,
                                request.status,
                            ),
                        )
                        moved += cursor.getInt(0)
                    }
                }
            moved.forEach { id -> db.execSQL("DELETE FROM pending_story_creation WHERE id = ?", arrayOf<Any?>(id)) }
        }
    }

/**
 * 두 테이블에 소유 회원 ID 를 더한다. 기존 행은 소유자를 모르므로 빈 값으로 두고, 다음 로그인 회원이
 * 넘겨받는다 — v2 까지는 로그아웃이 행을 전부 지웠으므로 남아 있는 행은 지금 로그인한 회원의 것이다.
 */
internal val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `pending_story_creation` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `story_completion_request` ADD COLUMN `ownerId` TEXT NOT NULL DEFAULT ''")
        }
    }

/**
 * v1 완성 행을 요청 행으로 바꾼다. 명령과 생성 결과를 해석할 수 있어야 하며, 서버 처리 여부를 모르므로
 * 상태는 PENDING 으로 두어 다음 새로고침이 복구 조회로 확정한다. 진행 JSON 은 없으면 빈 값으로 채운다.
 */
internal fun legacyCompletionToRequest(
    generationCommand: String?,
    completionCommand: String?,
    generation: String?,
    progress: String?,
    submittedAt: Long,
): StoryCompletionRequestEntity? {
    val command = decodeOrNull<CompletionCommandDto>(completionCommand) ?: return null
    if (generation == null || decodeOrNull<GenerationSnapshotDto>(generation) == null) return null
    return StoryCompletionRequestEntity(
        requestId = command.requestId,
        completionCommand = completionCommand.orEmpty(),
        generationCommand = generationCommand,
        generation = generation,
        progress = progress ?: encode(ProgressDto()),
        submittedAt = submittedAt,
        status = StoryCompletionRequestEntity.STATUS_PENDING,
    )
}
