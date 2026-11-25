package app.manyak.analytics.data.di

/** `BuildConfig` 는 `:app` 만 갖고 있으므로 composition root 가 이 형태로 주입한다. */
data class AnalyticsConfig(
    /** 비어 있으면 SDK 를 만들지 않고 디버그 로그로만 남긴다. */
    val apiKey: String,
    val isDebugBuild: Boolean,
)
