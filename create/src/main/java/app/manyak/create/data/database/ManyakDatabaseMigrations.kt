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
 * 편집 초안을 한 행 슬롯에서 draftId 별 여러 행으로 넓히고, 두 테이블에 처음 임시 저장 시각을 더한다.
 *
 * 기본 키가 바뀌어 테이블을 새로 만들어 옮긴다. 남아 있던 초안은 [LEGACY_DRAFT_ID_PREFIX] 로 시작하는
 * ID 를 받아 그대로 이어 만들 수 있고, 처음 저장 시각은 알 수 없어 비워 둔다 — 카드는 날짜 없이 맨 뒤에 온다.
 */
internal val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_story_creation_new` (" +
                    "`draftId` TEXT NOT NULL, `stage` TEXT NOT NULL, `generationCommand` TEXT, " +
                    "`completionCommand` TEXT, `generation` TEXT, `progress` TEXT, `keywordSnapshot` TEXT, " +
                    "`ownerId` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER, PRIMARY KEY(`draftId`))",
            )
            db.execSQL(
                "INSERT INTO pending_story_creation_new " +
                    "(draftId, stage, generationCommand, completionCommand, generation, progress, keywordSnapshot, " +
                    "ownerId, createdAt) " +
                    "SELECT '$LEGACY_DRAFT_ID_PREFIX' || id, stage, generationCommand, completionCommand, " +
                    "generation, progress, keywordSnapshot, ownerId, NULL FROM pending_story_creation",
            )
            db.execSQL("DROP TABLE pending_story_creation")
            db.execSQL("ALTER TABLE pending_story_creation_new RENAME TO pending_story_creation")
            db.execSQL("ALTER TABLE `story_completion_request` ADD COLUMN `createdAt` INTEGER")
        }
    }

/** 여러 초안 이전에 남아 있던 초안 행이 받는 ID 의 머리. 새 초안의 UUID 와 겹치지 않는다. */
internal const val LEGACY_DRAFT_ID_PREFIX = "legacy-"

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
