package app.manyak.analytics.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.manyak.analytics.domain.AnalyticsIdentity
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.data.di.DeviceDataStore
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play 스토어가 넘긴 설치 경로를 설치당 한 번 분석 사용자 속성으로 보낸다.
 *
 * 속성 이름은 웹 SDK 가 자동으로 쓰는 `initial_utm_*` 와 같아서 웹 가입과 앱 가입을 한 속성으로 묶어 본다.
 * 완료 표시는 `device_id` 가 아니라 기기 저장소에 둔다. 로그아웃으로 `device_id` 가 바뀌어도 설치 경로는
 * 그대로라서다. 일시 실패는 표시하지 않고 다음 실행 때 다시 읽는다. Play 는 이 값을 설치 후 90일 동안 준다.
 */
@Singleton
class InstallReferrerReporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val identity: AnalyticsIdentity,
        @param:DeviceDataStore private val dataStore: DataStore<Preferences>,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        fun start() {
            applicationScope.launch {
                if (isReported()) return@launch
                val referrer = readReferrer() ?: return@launch
                identity.setUserPropertiesOnce(installReferrerUserProperties(referrer))
                // setOnce 라 표시를 못 남겨 다음 실행에 다시 보내도 값은 바뀌지 않는다.
                runCatching { dataStore.edit { it[REPORTED_KEY] = true } }
            }
        }

        private suspend fun isReported(): Boolean =
            runCatching { dataStore.data.first()[REPORTED_KEY] == true }.getOrDefault(false)

        /** 다시 읽어야 하면 null 이다. Play 밖에서 설치해 읽을 값이 없으면 빈 문자열이다. */
        private suspend fun readReferrer(): String? =
            suspendCancellableCoroutine { continuation ->
                val client = InstallReferrerClient.newBuilder(context).build()
                continuation.invokeOnCancellation { client.endConnection() }
                client.startConnection(
                    object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            val referrer =
                                when (responseCode) {
                                    InstallReferrerResponse.OK ->
                                        runCatching { client.installReferrer.installReferrer.orEmpty() }.getOrNull()
                                    InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> ""
                                    else -> null
                                }
                            client.endConnection()
                            if (continuation.isActive) continuation.resume(referrer)
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }

        private companion object {
            val REPORTED_KEY = booleanPreferencesKey("install_referrer_reported")
        }
    }

private val utmKeys = setOf("utm_source", "utm_medium", "utm_campaign", "utm_content")

/** referrer 쿼리에서 값이 있는 UTM 만 골라 `initial_` 을 붙인 이름으로 돌려준다. */
internal fun installReferrerUserProperties(referrer: String): Map<String, String> =
    referrer
        .split('&')
        .mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val value = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrNull()
            if (key in utmKeys && !value.isNullOrBlank()) "initial_$key" to value else null
        }.toMap()
