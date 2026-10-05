package app.manyak.create.general.data

import app.manyak.create.general.data.database.GeneralDraftDao
import app.manyak.create.general.data.database.GeneralDraftEntity
import app.manyak.create.general.data.database.GeneralDraftSnapshot
import app.manyak.create.general.data.database.toDomain
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralDraftDaoTest {
    @Test
    fun `수락된 초안은 원문을 지우고 늦은 저장으로 다시 살아나지 않는다`() =
        runTest {
            val dao = FakeGeneralDraftDao()
            val draft = GeneralDraftEntity("a", "draft", "private-input", 12)
            assertTrue(dao.save(draft))
            dao.accept("a", "draft", "submission", 99)
            assertFalse(dao.save(draft))
            assertEquals("submission", dao.find("a", "draft")?.acceptedSubmissionId)
            assertEquals(12L, dao.find("a", "draft")?.createdAt)
            assertNull(dao.find("a", "draft")?.snapshot)
        }

    @Test
    fun `같은 초안 ID도 계정별로 격리하고 최초 저장 시각을 보존한다`() =
        runTest {
            val dao = FakeGeneralDraftDao()
            dao.save(GeneralDraftEntity("a", "draft", "a-input", 12))
            dao.save(GeneralDraftEntity("b", "draft", "b-input", 13))
            dao.save(GeneralDraftEntity("a", "draft", "a-updated", 20))
            assertEquals(12L, dao.find("a", "draft")?.createdAt)
            assertEquals("b-input", dao.find("b", "draft")?.snapshot)
            dao.deleteEditable("a", "draft")
            assertEquals("b-input", dao.find("b", "draft")?.snapshot)
        }

    @Test
    fun `한 번도 저장하지 않은 초안의 수락도 뒤늦은 저장을 막는다`() =
        runTest {
            val dao = FakeGeneralDraftDao()
            dao.accept("a", "draft", "submission", 99)
            assertFalse(dao.save(GeneralDraftEntity("a", "draft", "input", 100)))
            assertEquals(0, dao.deleteEditable("a", "draft"))
        }

    @Test
    fun `초안은 폼 식별자와 영속 이미지 경로를 손실 없이 복원한다`() {
        val json = Json { ignoreUnknownKeys = true }
        val form = GeneralStoryForm(title = "제목", cover = GeneralStoryImage("key", "file:///image.png", "/image.png"))
        val entity = GeneralDraftEntity("a", "draft", json.encodeToString(GeneralDraftSnapshot(form = form)), 12)
        assertEquals(form, entity.toDomain(json)?.form)
        assertNull(entity.copy(snapshot = "invalid").toDomain(json))
        assertNull(entity.copy(acceptedSubmissionId = "accepted").toDomain(json))
    }
}

private class FakeGeneralDraftDao : GeneralDraftDao {
    private val rows = mutableMapOf<Pair<String, String>, GeneralDraftEntity>()

    override fun observe(ownerId: String): Flow<List<GeneralDraftEntity>> =
        flowOf(rows.values.filter { it.ownerId == ownerId && it.acceptedSubmissionId == null })

    override suspend fun find(
        ownerId: String,
        draftId: String,
    ): GeneralDraftEntity? = rows[ownerId to draftId]

    override suspend fun upsert(entity: GeneralDraftEntity) {
        rows[entity.ownerId to entity.draftId] = entity
    }

    override suspend fun delete(
        ownerId: String,
        draftId: String,
    ): Int =
        if (rows.remove(ownerId to draftId) ==
            null
        ) {
            0
        } else {
            1
        }

    override suspend fun deleteEditable(
        ownerId: String,
        draftId: String,
    ): Int = if (find(ownerId, draftId)?.acceptedSubmissionId == null) delete(ownerId, draftId) else 0
}
