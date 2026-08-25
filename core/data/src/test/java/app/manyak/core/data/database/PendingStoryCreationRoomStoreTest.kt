package app.manyak.core.data.database

import app.manyak.core.domain.story.KeywordCharacterSnapshot
import app.manyak.core.domain.story.KeywordDraftSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
            val store = PendingStoryCreationRoomStore(dao, StandardTestDispatcher(testScheduler))
            val record = keywordDraft()

            store.write(record)

            assertEquals(record, store.read())
            assertEquals(record, store.record.first())

            store.clear()

            assertNull(store.read())
            assertNull(store.record.first())
        }

    @Test
    fun `사용자 데이터 정리는 여러 번 호출해도 성공한다`() =
        runTest {
            val dao = FakePendingStoryCreationDao()
            val store = PendingStoryCreationRoomStore(dao, StandardTestDispatcher(testScheduler))
            store.write(keywordDraft())

            assertTrue(store.clearUserData())
            assertTrue(store.clearUserData())
            assertNull(store.read())
        }

    @Test
    fun `사용자 데이터 정리 실패는 성공으로 삼키지 않는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao().apply { failClear = true }
            val store = PendingStoryCreationRoomStore(dao, StandardTestDispatcher(testScheduler))

            assertFalse(store.clearUserData())
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
    private val entity = MutableStateFlow<PendingStoryCreationEntity?>(null)
    var failClear: Boolean = false

    override fun observe(id: Int): Flow<PendingStoryCreationEntity?> = entity

    override suspend fun find(id: Int): PendingStoryCreationEntity? = entity.value

    override suspend fun upsert(entity: PendingStoryCreationEntity) {
        this.entity.value = entity
    }

    override suspend fun clear() {
        check(!failClear)
        entity.value = null
    }
}
