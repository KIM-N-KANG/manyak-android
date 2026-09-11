package app.manyak.notification.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.manyak.common.data.di.DeviceDataStore
import app.manyak.common.data.di.IoDispatcher
import app.manyak.notification.domain.NotificationPermissionPromptRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 테마 설정과 같은 기기 DataStore 파일을 쓴다. 읽기·쓰기 실패는 "아직 묻지 않음" 으로 본다 —
 * 실패의 결과가 한 번 더 묻는 것이라면 영영 묻지 않는 것보다 낫다.
 */
@Singleton
class NotificationPermissionPromptStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : NotificationPermissionPromptRepository {
        override suspend fun wasPrompted(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.data.first()[PROMPTED_KEY] == true }.getOrDefault(false)
            }

        override suspend fun markPrompted() {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[PROMPTED_KEY] = true } }
            }
        }

        private companion object {
            val PROMPTED_KEY = booleanPreferencesKey("notification_permission_prompted")
        }
    }
