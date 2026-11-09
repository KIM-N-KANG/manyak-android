package app.manyak.network.data.di

/**
 * 데이터 계층이 필요로 하는 빌드별 값. `BuildConfig` 는 `:app` 만 갖고 있으므로 composition root 가
 * 이 형태로 주입한다.
 */
data class DataLayerConfig(
    val apiBaseUrl: String,
    val isDebugBuild: Boolean,
    /** 서버에 함께 보내는 앱 버전. 모든 요청의 헤더와 피드백 본문에 붙어 어느 버전에서 왔는지 남긴다. */
    val appVersion: String,
    /** 웹 서비스 origin. 채팅 공유처럼 사용자에게 건네는 링크는 앱이 아니라 웹 주소다. */
    val webBaseUrl: String,
)
