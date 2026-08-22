package app.manyak.core.data.interceptor

import app.manyak.core.data.api.HEADER_DEVICE_ID
import app.manyak.core.data.datastore.DeviceIdStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/** `device_id` 를 읽지 못해 요청을 보내지 않았다. 빈 헤더로 보내는 것보다 실패가 낫다. */
class DeviceIdUnavailableException : IOException("device_id 를 준비하지 못해 요청을 보내지 않았다")

/**
 * `X-Manyak-Device-Id` 를 **모든 요청**에 싣는다.
 *
 * 특히 첫 로그인 요청에서 빠지면 서버가 회원 체험 잔여를 소진 상태로 시드하고, 그 시드는 정상 앱
 * 흐름에서 자동으로 되돌아가지 않는다. 그래서 값이 없으면 빈 문자열을 싣지 않고 요청 자체를 막는다.
 */
class DeviceIdInterceptor
    @Inject
    constructor(
        private val deviceIdStore: DeviceIdStore,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val deviceId = runBlocking { deviceIdStore.requireDeviceId() } ?: throw DeviceIdUnavailableException()
            val request =
                chain
                    .request()
                    .newBuilder()
                    .header(HEADER_DEVICE_ID, deviceId)
                    .build()
            return chain.proceed(request)
        }
    }
