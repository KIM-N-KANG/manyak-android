package app.manyak.chat.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.chat.entity.ChatInputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `저장된 값이 없으면 웹과 같은 기본값을 돌려준다`() =
        runTest {
            val store = store(fileDataStore())

            assertEquals(ChatInputMode.BLOCK, store.inputMode())
            assertTrue(store.choicesEnabled())
            assertFalse(store.realtimeImageEnabled())
            assertFalse(store.isChoicesHintSeen())
            assertFalse(store.isChatTourSeen())
        }

    @Test
    fun `저장한 값을 다시 읽는다`() =
        runTest {
            val store = store(fileDataStore())

            store.setInputMode(ChatInputMode.PLAIN)
            store.setChoicesEnabled(false)
            store.markChoicesHintSeen()
            store.markChatTourSeen()
            store.setRealtimeImageEnabled(true)

            assertEquals(ChatInputMode.PLAIN, store.inputMode())
            assertFalse(store.choicesEnabled())
            assertTrue(store.isChoicesHintSeen())
            assertTrue(store.isChatTourSeen())
            assertTrue(store.realtimeImageEnabled())
        }

    @Test
    fun `알 수 없는 입력 모드가 저장돼 있으면 기본값으로 읽는다`() =
        runTest {
            // 값 이름이 바뀌거나 손상된 경우다. 예외로 올리면 채팅방이 열리지 않는다.
            val dataStore = fileDataStore()
            dataStore.edit { preferences -> preferences[stringPreferencesKey("chat_input_mode")] = "SPEECH" }

            assertEquals(ChatInputMode.BLOCK, store(dataStore).inputMode())
        }

    @Test
    fun `읽지 못하면 기본값을 돌려주고 저장 실패는 삼킨다`() =
        runTest {
            // 설정 하나를 읽지 못했다고 채팅방이 열리지 않거나, 저장이 실패했다고 예외가 화면까지
            // 올라가서는 안 된다.
            val store = store(FailingDataStore())

            assertEquals(ChatInputMode.BLOCK, store.inputMode())
            assertTrue(store.choicesEnabled())
            assertFalse(store.realtimeImageEnabled())
            assertFalse(store.isChoicesHintSeen())

            store.setInputMode(ChatInputMode.PLAIN)
            store.setChoicesEnabled(false)
            store.markChoicesHintSeen()
        }

    @Test
    fun `완료 횟수는 저장소를 다시 만들어도 유지하며 3에서 멈춘다`() =
        runTest {
            val dataStore = fileDataStore()
            assertEquals(1, store(dataStore).recordCompletedTurn())
            assertEquals(2, store(dataStore).recordCompletedTurn())
            repeat(4) { assertEquals(3, store(dataStore).recordCompletedTurn()) }
        }

    @Test
    fun `동시 완료에서도 두 번째 도달은 한 번만 반환한다`() =
        runTest {
            val store = store(fileDataStore())
            val counts = List(5) { async { store.recordCompletedTurn() } }.awaitAll()
            assertEquals(1, counts.count { it == 2 })
            assertEquals(listOf(1, 2, 3, 3, 3), counts.filterNotNull().sorted())
        }

    @Test
    fun `완료 횟수를 저장하지 못하면 안내 판단에 쓸 값을 반환하지 않는다`() =
        runTest {
            assertNull(store(FailingDataStore()).recordCompletedTurn())
        }

    @Test
    fun `두 번째 완료를 계산했어도 디스크 쓰기가 실패하면 안내를 열지 않는다`() =
        runTest {
            val initial = preferencesOf(intPreferencesKey("chat_completed_turn_count") to 1)
            val failingWrite =
                object : DataStore<Preferences> {
                    override val data = flowOf(initial)

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                        transform(initial)
                        throw IOException("디스크 쓰기 실패")
                    }
                }
            assertNull(store(failingWrite).recordCompletedTurn())
        }

    private fun kotlinx.coroutines.test.TestScope.store(dataStore: DataStore<Preferences>) =
        ChatPreferencesStore(dataStore = dataStore, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

    private fun fileDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { temporaryFolder.newFile("chat.preferences_pb") }
}

/** 읽기·쓰기가 모두 실패하는 저장소. */
private class FailingDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("읽을 수 없음") }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("쓸 수 없음")
}
