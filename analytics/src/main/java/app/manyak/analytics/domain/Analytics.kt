package app.manyak.analytics.domain

import app.manyak.analytics.entity.AnalyticsEvent

/** 분석 이벤트 발행. 화면과 ViewModel 은 이 계약만 보고 SDK 를 모른다. */
interface Analytics {
    fun track(event: AnalyticsEvent)
}

/** 키가 없는 빌드·테스트의 대체. 아무것도 보내지 않는다. */
object NoOpAnalytics : Analytics {
    override fun track(event: AnalyticsEvent) = Unit
}
