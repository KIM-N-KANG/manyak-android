package app.manyak.analytics.presentation

import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.common.presentation.story.CreateTutorialReport

/** 첫 진입 안내 절차가 알린 일을 이벤트로 바꾼다. 간편 제작 화면과 제작 탭이 같은 이름을 보낸다. */
fun CreateTutorialReport.toAnalyticsEvent(): AnalyticsEvent =
    when (this) {
        is CreateTutorialReport.StepViewed -> AnalyticsEvent.CreateTutorialStepViewed(stage)
        is CreateTutorialReport.Skipped -> AnalyticsEvent.CreateTutorialSkipButtonClicked(stage)
        is CreateTutorialReport.Completed -> AnalyticsEvent.CreateTutorialCompleted(toOriginals)
    }
