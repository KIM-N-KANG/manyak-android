package app.manyak.create.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralDraftMigrationTest {
    @Test
    fun supportedVersionsPreserveSimpleDraftsAndCompletionRequests() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (version in 1..5) {
            val name = "general-migration-$version.db"
            context.deleteDatabase(name)
            try {
                createLegacy(context, name, version)
                val room =
                    Room
                        .databaseBuilder(context, ManyakDatabase::class.java, name)
                        .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                        ).build()
                try {
                    val db = room.openHelper.writableDatabase
                    assertDraftPreserved(db, version)
                    if (version > 1) {
                        val completion = "SELECT completionCommand, serverDraftVersion FROM story_completion_request"
                        db.query(completion).use { cursor ->
                            check(cursor.moveToFirst())
                            assertEquals("completion-payload", cursor.getString(0))
                            // 기존 요청은 서버 초안 연결이 없는 기기 안 세션의 것으로 남는다.
                            assertTrue(cursor.isNull(1))
                        }
                    }
                    assertOwnerKeyIsolated(db)
                } finally {
                    room.close()
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }

    private fun createLegacy(
        context: Context,
        name: String,
        version: Int,
    ) {
        val schema =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open("app.manyak.create.data.database.ManyakDatabase/$version.json")
                .bufferedReader()
                .use { JSONObject(it.readText()).getJSONObject("database") }
        val callback =
            object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        db.execSQL(
                            entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")),
                        )
                    }
                    val idColumn = if (version >= 4) "draftId" else "id"
                    val id = if (version >= 4) "'old-draft'" else "0"
                    db.execSQL(
                        "INSERT INTO pending_story_creation ($idColumn, stage, keywordSnapshot) " +
                            "VALUES ($id, 'KEYWORD_DRAFT', 'original-keyword-payload')",
                    )
                    if (version > 1) {
                        db.execSQL(
                            "INSERT INTO story_completion_request " +
                                "(requestId, completionCommand, generation, progress, submittedAt, status) " +
                                "VALUES ('request', 'completion-payload', 'generation', 'progress', 42, 'PENDING')",
                        )
                    }
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) = Unit
            }
        FrameworkSQLiteOpenHelperFactory()
            .create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(callback)
                    .build(),
            ).use { it.writableDatabase }
    }

    private fun assertDraftPreserved(
        db: SupportSQLiteDatabase,
        version: Int,
    ) {
        val draft = "SELECT draftId, keywordSnapshot, ownerId, serverDraftVersion FROM pending_story_creation"
        db.query(draft).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(if (version >= 4) "old-draft" else "legacy-0", cursor.getString(0))
            assertEquals("original-keyword-payload", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            // 기존 초안은 서버로 옮기지 않고 기기 안 세션으로 연다.
            assertTrue(cursor.isNull(3))
        }
    }

    private fun assertOwnerKeyIsolated(db: SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM general_story_draft").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        listOf("owner-a", "owner-b").forEach { owner ->
            db.execSQL(
                "INSERT INTO general_story_draft (ownerId, draftId, snapshot, createdAt) " +
                    "VALUES (?, 'same-id', '{}', 1)",
                arrayOf(owner),
            )
        }
        db.query("SELECT COUNT(*) FROM general_story_draft").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
    }
}
