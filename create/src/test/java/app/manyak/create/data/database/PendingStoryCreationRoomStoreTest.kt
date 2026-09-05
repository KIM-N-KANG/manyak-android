package app.manyak.create.data.database

import app.manyak.common.entity.story.CreationStage
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
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

            assertTrue(store.write(record))

            assertEquals(record, store.read())
            assertEquals(record, store.record.first())
            assertEquals(CreationStage.KEYWORD_DRAFT, store.progress.first()?.stage)

            assertTrue(store.discard())

            assertNull(store.read())
            assertNull(store.record.first())
            assertNull(store.progress.first())
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

    @Test
    fun `쓰기 실패는 저장 성공으로 보고하지 않는다`() =
        runTest {
            val dao = FakePendingStoryCreationDao().apply { failUpsert = true }
            val store = PendingStoryCreationRoomStore(dao, StandardTestDispatcher(testScheduler))

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
    private val entity = MutableStateFlow<PendingStoryCreationEntity?>(null)
    var failUpsert: Boolean = false
    var failClear: Boolean = false

    override fun observe(id: Int): Flow<PendingStoryCreationEntity?> = entity

    override suspend fun find(id: Int): PendingStoryCreationEntity? = entity.value

    override suspend fun upsert(entity: PendingStoryCreationEntity) {
        check(!failUpsert)
        this.entity.value = entity
    }

    override suspend fun clear() {
        check(!failClear)
        entity.value = null
    }
}
