package app.manyak.chat.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.chat.domain.ChatPreferencesRepository
import app.manyak.chat.entity.ChatInputMode
import app.manyak.common.data.di.DeviceDataStore
import app.manyak.common.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅방 기기 설정의 정본.
 *
 * 기기 귀속 값이라 `device_id` 와 같은 DataStore 파일을 쓴다. **사용자 귀속이 아니므로
 * `UserScopedStore` 를 구현하지 않는다** — 로그아웃 정리에서 빠진 것이 아니라 대상이 아니다.
 *
 * 읽기·쓰기 실패를 모두 삼킨다. 설정 하나를 읽지 못했다고 채팅방이 열리지 않거나, 저장에 실패했다고
 * 방금 누른 선택이 화면에서 되돌아가서는 안 된다. 설정 실패는 다음 실행에서 기본값으로 돌아간다.
 * 완료 횟수는 저장에 성공한 값만 반환해 기록 실패를 안내 노출의 근거로 쓰지 않는다.
 */
@Suppress("TooManyFunctions") // 기기 설정마다 읽기·쓰기 한 쌍이다.
@Singleton
class ChatPreferencesStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ChatPreferencesRepository {
        override suspend fun inputMode(): ChatInputMode {
            val saved = read(INPUT_MODE_KEY)
            return ChatInputMode.entries.firstOrNull { mode -> mode.name == saved } ?: DEFAULT_INPUT_MODE
        }

        override suspend fun setInputMode(mode: ChatInputMode) = write { it[INPUT_MODE_KEY] = mode.name }

        override suspend fun choicesEnabled(): Boolean = read(CHOICES_ENABLED_KEY) ?: DEFAULT_CHOICES_ENABLED

        override suspend fun setChoicesEnabled(enabled: Boolean) = write { it[CHOICES_ENABLED_KEY] = enabled }

        override suspend fun realtimeImageEnabled(): Boolean =
            read(REALTIME_IMAGE_ENABLED_KEY) ?: DEFAULT_REALTIME_IMAGE_ENABLED

        override suspend fun setRealtimeImageEnabled(enabled: Boolean) =
            write { it[REALTIME_IMAGE_ENABLED_KEY] = enabled }

        override suspend fun recordCompletedTurn(): Int? =
            withContext(ioDispatcher) {
                try {
                    val saved =
                        dataStore.edit { values ->
                            val count = (values[COMPLETED_TURN_COUNT_KEY] ?: 0).coerceIn(0, MAX_COMPLETED_TURNS)
                            values[COMPLETED_TURN_COUNT_KEY] = (count + 1).coerceAtMost(MAX_COMPLETED_TURNS)
                        }
                    saved[COMPLETED_TURN_COUNT_KEY]
                } catch (_: IOException) {
                    null
                }
            }

        override suspend fun isChoicesHintSeen(): Boolean = read(CHOICES_HINT_SEEN_KEY) ?: false

        override suspend fun markChoicesHintSeen() = write { it[CHOICES_HINT_SEEN_KEY] = true }

        override suspend fun isChatTourSeen(): Boolean = read(CHAT_TOUR_SEEN_KEY) ?: false

        override suspend fun markChatTourSeen() = write { it[CHAT_TOUR_SEEN_KEY] = true }

        private suspend fun <T> read(key: Preferences.Key<T>): T? =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[key] }.getOrNull()
            }

        private suspend fun write(transform: (MutablePreferences) -> Unit) {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit(transform) }
            }
        }

        private companion object {
            val INPUT_MODE_KEY = stringPreferencesKey("chat_input_mode")
            val CHOICES_ENABLED_KEY = booleanPreferencesKey("chat_choices_enabled")
            val CHOICES_HINT_SEEN_KEY = booleanPreferencesKey("chat_choices_hint_seen")
            val CHAT_TOUR_SEEN_KEY = booleanPreferencesKey("chat_tour_seen")
            val REALTIME_IMAGE_ENABLED_KEY = booleanPreferencesKey("chat_realtime_image_enabled")

            /** 웹과 같은 기본값이다. 처음 들어온 사용자가 두 클라이언트에서 다른 화면을 보면 안 된다. */
            val DEFAULT_INPUT_MODE = ChatInputMode.BLOCK
            const val DEFAULT_CHOICES_ENABLED = true
            const val DEFAULT_REALTIME_IMAGE_ENABLED = false
            val COMPLETED_TURN_COUNT_KEY = intPreferencesKey("chat_completed_turn_count")
            const val MAX_COMPLETED_TURNS = 3
        }
    }
