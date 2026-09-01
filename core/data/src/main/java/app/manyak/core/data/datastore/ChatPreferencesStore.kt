package app.manyak.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.core.data.di.DeviceDataStore
import app.manyak.core.data.di.IoDispatcher
import app.manyak.core.domain.chat.ChatInputMode
import app.manyak.core.domain.chat.ChatPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅방 기기 설정의 정본.
 *
 * 기기 귀속 값이라 `device_id` 와 같은 DataStore 파일을 쓴다. **사용자 귀속이 아니므로
 * `UserScopedStore` 를 구현하지 않는다** — 로그아웃 정리에서 빠진 것이 아니라 대상이 아니다.
 *
 * 읽기·쓰기 실패를 모두 삼킨다. 설정 하나를 읽지 못했다고 채팅방이 열리지 않거나, 저장에 실패했다고
 * 방금 누른 선택이 화면에서 되돌아가서는 안 된다. 실패의 결과는 "다음 실행에서 기본값"뿐이다.
 */
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

        override suspend fun isChoicesHintSeen(): Boolean = read(CHOICES_HINT_SEEN_KEY) ?: false

        override suspend fun markChoicesHintSeen() = write { it[CHOICES_HINT_SEEN_KEY] = true }

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

            /** 웹과 같은 기본값이다. 처음 들어온 사용자가 두 클라이언트에서 다른 화면을 보면 안 된다. */
            val DEFAULT_INPUT_MODE = ChatInputMode.BLOCK
            const val DEFAULT_CHOICES_ENABLED = true
        }
    }
