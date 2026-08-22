package app.manyak.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.core.data.di.DeviceDataStore
import app.manyak.core.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `X-Manyak-Device-Id` 의 정본. 앱이 첫 실행 시 UUID 를 만들어 보관하고, 분석 SDK 에도 같은 값을 주입한다.
 *
 * **빈 값을 헤더에 싣지 않는다**(하네스 §3-3-4). 서버는 누락과 공백을 같게 취급하고, 계정 생성 시
 * 이 헤더가 비어 있으면 회원 체험 잔여를 소진 상태로 시드한다. 그 시드는 정상 앱 흐름에서 자동으로
 * 되돌아가지 않으므로, 읽지 못했을 때는 빈 문자열 대신 null 을 돌려주고 호출부가 요청을 막는다.
 */
@Singleton
class DeviceIdStore
    @Inject
    constructor(
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** 없으면 만들어 저장한 뒤 돌려준다. 저장에 실패하면 null 이며 그 상태로 로그인을 보내지 않는다. */
        suspend fun requireDeviceId(): String? =
            withContext(ioDispatcher) {
                readOrNull()?.let { return@withContext it }
                val generated = UUID.randomUUID().toString()
                runCatching {
                    dataStore.edit { preferences ->
                        // 동시 호출이 각각 만든 값으로 덮어쓰지 않도록 이미 있으면 그 값을 유지한다.
                        val existing = preferences[DEVICE_ID_KEY]
                        if (existing.isNullOrBlank()) preferences[DEVICE_ID_KEY] = generated
                    }
                }.getOrNull() ?: return@withContext null
                readOrNull()
            }

        /** 로그아웃 정리의 마지막 단계에서 저널에 고정해 둔 새 값으로 교체한다. 멱등하다. */
        suspend fun replaceWith(newDeviceId: String) {
            require(newDeviceId.isNotBlank()) { "device_id 는 빈 값일 수 없다" }
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it[DEVICE_ID_KEY] = newDeviceId } }
            }
        }

        private suspend fun readOrNull(): String? =
            runCatching { dataStore.data.first()[DEVICE_ID_KEY] }.getOrNull()?.takeIf { it.isNotBlank() }

        private companion object {
            val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        }
    }
