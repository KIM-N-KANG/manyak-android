package app.manyak.create.data.database

import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingStoryCreationRoomStoreTest {
    @Test
    fun `단일 행을 쓰고 읽고 관찰한 뒤 지운다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))
            val record = keywordDraft()

            assertTrue(store.write(record))

            assertEquals(record, store.read())
            assertEquals(record, store.record.first())

            assertTrue(store.clear())

            assertNull(store.read())
            assertNull(store.record.first())
        }

    @Test
    fun `다른 회원의 초안은 보이지 않고 같은 회원이 돌아오면 다시 보인다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            val profile = FakeUserProfileRepository()
            val store = PendingStoryCreationRoomStore(dao, profile, StandardTestDispatcher(testScheduler))
            store.write(keywordDraft())

            // 로그아웃 — 행은 남지만 보이지 않고, 쓰기도 막힌다.
            profile.profile.value = null
            assertNull(store.read())
            assertNull(store.record.first())
            assertFalse(store.write(keywordDraft()))
            assertFalse(store.clear())
            assertEquals(1, dao.rows.size)

            profile.profile.value = sampleProfile("user-b")
            assertNull(store.read())

            profile.profile.value = sampleProfile("user-a")
            assertEquals(keywordDraft(), store.read())
        }

    @Test
    fun `소유자 없는 이전 버전 행은 지금 회원이 넘겨받는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            dao.rows += keywordDraft().toEntity()
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))

            assertNull(store.read())
            store.claimUnowned()

            assertEquals(keywordDraft(), store.read())
        }

    @Test
    fun `쓰기 실패는 저장 성공으로 보고하지 않는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao().apply { failUpsert = true }
            val store =
                PendingStoryCreationRoomStore(dao, FakeUserProfileRepository(), StandardTestDispatcher(testScheduler))

            assertFalse(store.write(keywordDraft()))
            assertNull(store.read())
        }

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

    override fun observe(
        id: Int,
        ownerId: String,
    ): Flow<PendingStoryCreationEntity?> = version.map { rows.firstOrNull { it.id == id && it.ownerId == ownerId } }

    override suspend fun find(
        id: Int,
        ownerId: String,
    ): PendingStoryCreationEntity? = rows.firstOrNull { it.id == id && it.ownerId == ownerId }

    override suspend fun upsert(entity: PendingStoryCreationEntity) {
        check(!failUpsert)
        rows.removeAll { it.id == entity.id }
        rows += entity
        version.value++
    }

    override suspend fun clear(
        id: Int,
        ownerId: String,
    ) {
        rows.removeAll { it.id == id && it.ownerId == ownerId }
        version.value++
    }

    override suspend fun claimUnowned(ownerId: String) {
        val claimed = rows.map { if (it.ownerId.isEmpty()) it.copy(ownerId = ownerId) else it }
        rows.clear()
        rows += claimed
        version.value++
    }
}
