package app.manyak.analytics.presentation

import androidx.compose.runtime.staticCompositionLocalOf
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.domain.NoOpAnalytics

/**
 * ViewModel 을 거치지 않는 탐색 탭·노출처럼 화면이 직접 보내는 이벤트의 통로. 루트가 한 번 제공한다.
 * 기본값이 no-op 인 이유는 프리뷰와 테스트 트리가 제공자 없이도 그려져야 해서다.
 */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }
