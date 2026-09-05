package app.manyak.network.data.di

/**
 * 데이터 계층이 필요로 하는 빌드별 값. `BuildConfig` 는 `:app` 만 갖고 있으므로 composition root 가
 * 이 형태로 주입한다.
 */
data class DataLayerConfig(
    val apiBaseUrl: String,
    val isDebugBuild: Boolean,
    /** 서버에 함께 보내는 앱 버전. 피드백이 어느 버전에서 왔는지 화면 입력 없이 붙인다. */
    val appVersion: String,
)
