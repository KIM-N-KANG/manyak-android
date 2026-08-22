package app.manyak.core.data.di

/**
 * 데이터 계층이 필요로 하는 빌드별 값. `BuildConfig` 는 `:app` 만 갖고 있으므로 composition root 가
 * 이 형태로 주입한다.
 */
data class DataLayerConfig(
    val apiBaseUrl: String,
    val isDebugBuild: Boolean,
)

/**
 * 소셜 로그인 제공자 키. 빈 문자열이면 그 제공자로 로그인을 **시작하지 않는다** —
 * 빈 키로 SDK 를 호출하면 원인을 알기 어려운 제공자 오류로 흩어진다.
 */
data class SocialAuthConfig(
    val googleServerClientId: String,
    val kakaoNativeAppKey: String,
)
