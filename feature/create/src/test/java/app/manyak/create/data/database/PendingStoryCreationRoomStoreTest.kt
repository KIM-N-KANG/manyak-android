package app.manyak.create.data.database

import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.testing.FakeUserProfileRepository
import app.manyak.create.testing.sampleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingStoryCreationRoomStoreTest {
    @Test
    fun `초안을 쓰고 읽고 관찰한 뒤 지운다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))
            val record = keywordDraft()

            assertTrue(store.write("draft-a", record))

            assertEquals(record, store.read("draft-a"))
            assertEquals(listOf(record), store.drafts.first().map { it.record })

            assertTrue(store.clear("draft-a"))

            assertNull(store.read("draft-a"))
            assertTrue(store.drafts.first().isEmpty())
        }

    @Test
    fun `초안은 ID 별로 공존하고 하나를 지워도 다른 초안은 남는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))
            val generating = PendingStoryCreation.GeneratingStorylines(generationCommand())

            store.write("draft-a", keywordDraft())
            store.write("draft-b", generating)
            store.clear("draft-a")

            assertNull(store.read("draft-a"))
            assertEquals(generating, store.read("draft-b"))
        }

    @Test
    fun `처음 저장 시각은 단계가 바뀌어도 유지되고 이전 버전 행은 시각 없이 남는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            dao.rows += keywordDraft().toEntity("legacy-0", "user-a", createdAt = null)
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))

            store.write("draft-a", keywordDraft())
            val firstSavedAt = dao.rows.single { it.draftId == "draft-a" }.createdAt
            store.write("draft-a", PendingStoryCreation.GeneratingStorylines(generationCommand()))
            store.write("legacy-0", PendingStoryCreation.GeneratingStorylines(generationCommand()))

            assertNotNull(firstSavedAt)
            assertEquals(firstSavedAt, dao.rows.single { it.draftId == "draft-a" }.createdAt)
            assertEquals("STORYLINE_GENERATION", dao.rows.single { it.draftId == "draft-a" }.stage)
            assertNull(dao.rows.single { it.draftId == "legacy-0" }.createdAt)
        }

    @Test
    fun `다른 회원의 초안은 보이지 않고 같은 회원이 돌아오면 다시 보인다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            val profile = FakeUserProfileRepository()
            val store = PendingStoryCreationRoomStore(dao, profile, StandardTestDispatcher(testScheduler))
            store.write("draft-a", keywordDraft())

            // 로그아웃 — 행은 남지만 보이지 않고, 쓰기도 막힌다.
            profile.profile.value = null
            assertNull(store.read("draft-a"))
            assertTrue(store.drafts.first().isEmpty())
            assertFalse(store.write("draft-a", keywordDraft()))
            assertFalse(store.clear("draft-a"))
            assertEquals(1, dao.rows.size)

            profile.profile.value = sampleProfile("user-b")
            assertNull(store.read("draft-a"))

            profile.profile.value = sampleProfile("user-a")
            assertEquals(keywordDraft(), store.read("draft-a"))
        }

    @Test
    fun `소유자 없는 이전 버전 행은 지금 회원이 넘겨받는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            dao.rows += keywordDraft().toEntity("legacy-0")
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))

            assertNull(store.read("legacy-0"))
            store.claimUnowned()

            assertEquals(keywordDraft(), store.read("legacy-0"))
        }

    @Test
    fun `쓰기 실패는 저장 성공으로 보고하지 않는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao().apply { failUpsert = true }
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))

            assertFalse(store.write("draft-a", keywordDraft()))
            assertNull(store.read("draft-a"))
        }

    private fun generationCommand() =
        StorylineGenerationCommand(
            requestId = "req-1",
            genreTagIds = listOf(1L),
            customGenreTags = emptyList(),
            protagonist =
                StoryCharacterInput(
                    name = null,
                    gender = null,
                    featureTagIds = emptyList(),
                    customTags = emptyList(),
                ),
            supportingCharacters = emptyList(),
            parentCreationId = null,
            isRegenerated = false,
        )

    private fun keywordDraft() =
        PendingStoryCreation.KeywordDraft(
            KeywordDraftSnapshot(
                selectedGenreTagIds = listOf(1L),
                customGenreTags = emptyList(),
                protagonist =
                    KeywordCharacterSnapshot(
                        name = "홍길동",
                        gender = null,
                        selectedTagIds = emptyList(),
                        customTags = emptyList(),
                    ),
                supportingCharacters = emptyList(),
            ),
        )
}

private class FakePendingStoryCreationDao : PendingStoryCreationDao {
    val rows = mutableListOf<PendingStoryCreationEntity>()
    private val version = MutableStateFlow(0)
    var failUpsert: Boolean = false

    override fun observeAll(ownerId: String): Flow<List<PendingStoryCreationEntity>> =
        version.map { rows.filter { it.ownerId == ownerId } }

    override suspend fun find(
        draftId: String,
        ownerId: String,
    ): PendingStoryCreationEntity? = rows.firstOrNull { it.draftId == draftId && it.ownerId == ownerId }

    override suspend fun upsert(entity: PendingStoryCreationEntity) {
        check(!failUpsert)
        rows.removeAll { it.draftId == entity.draftId }
        rows += entity
        version.value++
    }

    override suspend fun clear(
        draftId: String,
        ownerId: String,
    ) {
        rows.removeAll { it.draftId == draftId && it.ownerId == ownerId }
        version.value++
    }

    override suspend fun claimUnowned(ownerId: String) {
        val claimed = rows.map { if (it.ownerId.isEmpty()) it.copy(ownerId = ownerId) else it }
        rows.clear()
        rows += claimed
        version.value++
    }
}
