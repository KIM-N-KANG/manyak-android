package app.manyak.core.data.provider

import android.content.Context
import app.manyak.core.data.di.SocialAuthConfig
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카카오 SDK 초기화의 단일 지점. **`Application.onCreate` 에서 Activity 보다 먼저** 호출한다.
 *
 * 외부 카카오계정 로그인 중에 프로세스가 종료되면 리다이렉트가 새 프로세스의 리다이렉트 Activity 를
 * 먼저 띄운다. 초기화를 로그인 제공자 호출 시점으로 미루면 그 Activity 가 초기화되지 않은 SDK 를
 * 만나므로, 진입점과 무관하게 초기화가 끝나 있도록 앱 시작에서 한 번 수행한다.
 *
 * 앱 키가 비어 있는 빌드에서는 초기화하지 않고 [isInitialized] 를 false 로 둔다. 그 상태로 SDK 를
 * 부르면 원인을 알기 어려운 오류로 흩어지므로, 호출부가 설정 오류를 **즉시 실패로** 드러낸다.
 */
@Singleton
class KakaoSdkInitializer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val config: SocialAuthConfig,
    ) {
        @Volatile
        var isInitialized: Boolean = false
            private set

        /** 여러 번 호출해도 안전하다. 초기화되어 있으면 true. */
        fun initialize(): Boolean {
            if (isInitialized) return true
            synchronized(this) {
                if (isInitialized) return true
                if (config.kakaoNativeAppKey.isBlank()) return false
                KakaoSdk.init(context, config.kakaoNativeAppKey)
                isInitialized = true
                return true
            }
        }
    }
