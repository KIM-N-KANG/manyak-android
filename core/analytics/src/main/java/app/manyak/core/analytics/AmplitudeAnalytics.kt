package app.manyak.core.analytics

import android.content.Context
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Amplitude 배선.
 *
 * `device_id` 주입 전에 들어온 이벤트는 그때의 프로퍼티째로 쌓아 두었다가 주입 직후 순서대로 보낸다.
 * SDK 큐에 먼저 넣으면 SDK 생성 값이 식별자로 굳는다.
 */
@Singleton
class AmplitudeAnalytics
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val config: AnalyticsConfig,
    ) : Analytics,
        AnalyticsIdentity {
        private val amplitude: Amplitude? =
            config.apiKey
                .takeIf { it.isNotBlank() }
                ?.let { key -> Amplitude(Configuration(apiKey = key, context = context)) }

        private val lock = Any()
        private var deviceIdReady = false
        private val pending = ArrayDeque<Pair<String, Map<String, Any>>>()

        @Volatile
        private var userId: String? = null

        override fun track(event: AnalyticsEvent) {
            val payload = event.payload(userId)
            synchronized(lock) {
                if (!deviceIdReady) {
                    pending.addLast(event.name to payload)
                    return
                }
            }
            send(event.name, payload)
        }

        override fun setDeviceId(deviceId: String) {
            amplitude?.setDeviceId(deviceId)
            val queued =
                synchronized(lock) {
                    deviceIdReady = true
                    pending.toList().also { pending.clear() }
                }
            queued.forEach { (name, payload) -> send(name, payload) }
        }

        override fun setUser(userId: String) {
            this.userId = userId
            amplitude?.setUserId(userId)
        }

        override fun clearUser() {
            userId = null
            amplitude?.setUserId(null)
        }

        private fun send(
            name: String,
            payload: Map<String, Any>,
        ) {
            if (config.isDebugBuild) Log.d(TAG, "$name $payload")
            amplitude?.track(name, payload)
        }

        private companion object {
            const val TAG = "Analytics"
        }
    }

/** 공통 프로퍼티를 붙이고 값이 없는 프로퍼티는 뺀다. 웹의 `track()` 과 같은 모양이다. */
private fun AnalyticsEvent.payload(userId: String?): Map<String, Any> =
    buildMap {
        properties.forEach { (key, value) -> if (value != null) put(key, value) }
        put("screen_name", screenName)
        put("is_logged_in", userId != null)
        if (userId != null) put("user_id", userId)
    }
