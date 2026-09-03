package app.manyak.core.analytics

import androidx.compose.runtime.staticCompositionLocalOf

/** 분석 이벤트 발행. 화면과 ViewModel 은 이 계약만 보고 SDK 를 모른다. */
interface Analytics {
    fun track(event: AnalyticsEvent)
}

/** 키가 없는 빌드·테스트의 대체. 아무것도 보내지 않는다. */
object NoOpAnalytics : Analytics {
    override fun track(event: AnalyticsEvent) = Unit
}

/**
 * ViewModel 을 거치지 않는 탐색 탭·노출처럼 화면이 직접 보내는 이벤트의 통로. 루트가 한 번 제공한다.
 * 기본값이 no-op 인 이유는 프리뷰와 테스트 트리가 제공자 없이도 그려져야 해서다.
 */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }
