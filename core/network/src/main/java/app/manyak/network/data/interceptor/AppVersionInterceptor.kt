package app.manyak.network.data.interceptor

import app.manyak.network.data.api.HEADER_APP_VERSION
import app.manyak.network.data.di.DataLayerConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * `X-Manyak-App-Version` 을 **모든 요청**에 싣는다.
 *
 * 서버가 요청이 어느 앱 버전에서 왔는지 알아야 구버전이 얼마나 남았는지 보고 계약을 바꿀 시점을
 * 판단할 수 있다. 이미 사용자 기기에 나간 앱에는 나중에 심을 수 없어서, 읽는 쪽이 생기기 전에 먼저 싣는다.
 */
class AppVersionInterceptor
    @Inject
    constructor(
        private val config: DataLayerConfig,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response =
            chain.proceed(
                chain
                    .request()
                    .newBuilder()
                    .header(HEADER_APP_VERSION, config.appVersion)
                    .build(),
            )
    }
