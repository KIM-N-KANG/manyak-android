package app.manyak.common.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.data.di.DeviceDataStore
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.settings.ThemePreferenceRepository
import app.manyak.common.entity.settings.ThemeMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 테마 설정의 정본. 기기 귀속 값이라 `device_id` 와 같은 DataStore 파일을 쓰고,
 * **사용자 귀속이 아니므로 `UserScopedStore` 를 구현하지 않는다.**
 *
 * 읽기·쓰기 실패를 모두 삼킨다. 설정 하나를 읽지 못했다고 앱이 열리지 않거나, 저장에 실패했다고
 * 방금 바꾼 테마가 화면에서 되돌아가서는 안 된다. 실패의 결과는 "다음 실행에서 기본값"뿐이다.
 */
@Singleton
class ThemePreferencesStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ThemePreferenceRepository {
        override val themeMode: Flow<ThemeMode> =
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { preferences ->
                    val saved = preferences[THEME_MODE_KEY]
                    ThemeMode.entries.firstOrNull { mode -> mode.name == saved } ?: ThemeMode.SYSTEM
                }

        override suspend fun setThemeMode(mode: ThemeMode) {
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[THEME_MODE_KEY] = mode.name } }
            }
        }

        private companion object {
            val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        }
    }
