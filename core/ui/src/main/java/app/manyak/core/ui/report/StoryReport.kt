package app.manyak.core.ui.report

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.ReportSource
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryRepository
import app.manyak.common.entity.story.StoryReportReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 신고 시트가 그리는 상태. 화면 상태에 이 덩어리째 들어간다. */
data class StoryReportUiState(
    val isSheetOpen: Boolean = false,
    val reason: StoryReportReason? = null,
    val detail: String = "",
    val isSubmitting: Boolean = false,
)

/** 시트에서 올라오는 사용자 동작. */
sealed interface StoryReportAction {
    data object Open : StoryReportAction

    data object Close : StoryReportAction

    data class SelectReason(
        val reason: StoryReportReason,
    ) : StoryReportAction

    data class ChangeDetail(
        val detail: String,
    ) : StoryReportAction

    data object Submit : StoryReportAction
}

/** 상태 전이의 입력. 화면의 Event 가 이것을 감싸 나른다. */
sealed interface StoryReportChange {
    data class SheetVisibleChanged(
        val visible: Boolean,
    ) : StoryReportChange

    data class ReasonSelected(
        val reason: StoryReportReason,
    ) : StoryReportChange

    data class DetailChanged(
        val detail: String,
    ) : StoryReportChange

    data object SubmitRequested : StoryReportChange

    data object SubmitFailed : StoryReportChange
}

fun StoryReportUiState.reduceReport(change: StoryReportChange): StoryReportUiState =
    when (change) {
        is StoryReportChange.SheetVisibleChanged ->
            if (change.visible) {
                copy(isSheetOpen = true)
            } else {
                // 닫으면 입력을 비운다 — 다시 열었을 때 지난 신고의 사유가 남아 있으면 오발송이 된다.
                StoryReportUiState()
            }

        is StoryReportChange.ReasonSelected -> copy(reason = change.reason)

        is StoryReportChange.DetailChanged -> copy(detail = change.detail)

        StoryReportChange.SubmitRequested -> copy(isSubmitting = true)

        StoryReportChange.SubmitFailed -> copy(isSubmitting = false)
    }

/**
 * 신고 흐름. 스토리 상세와 채팅방이 절차를 그대로 공유하므로 화면마다 되풀이하지 않고 여기 둔다.
 *
 * 상태를 직접 들지 않는 이유는 화면 상태의 정본이 각 ViewModel 이기 때문이다 — 여기서 따로 들면
 * 두 벌이 되어 구성 변경에서 갈라진다. 전이는 [emit] 으로 올려보내고 결과만 [notify] 로 알린다.
 */
class StoryReportController(
    private val scope: CoroutineScope,
    private val repository: StoryRepository,
    private val analytics: Analytics,
    private val source: ReportSource,
    private val emit: suspend (StoryReportChange) -> Unit,
    private val notify: suspend (submitted: Boolean) -> Unit,
) {
    private var submitJob: Job? = null

    suspend fun handle(
        action: StoryReportAction,
        storyId: String?,
        state: StoryReportUiState,
    ) {
        when (action) {
            // 신고 대상이 아직 없으면 보낼 곳이 없다.
            StoryReportAction.Open ->
                if (storyId != null) {
                    analytics.track(AnalyticsEvent.ReportSheetOpened(storyId, source))
                    emit(StoryReportChange.SheetVisibleChanged(true))
                }

            // 전송 중에는 닫지 않는다 — 결과를 못 본 채 시트만 사라진다.
            StoryReportAction.Close -> if (!state.isSubmitting) emit(StoryReportChange.SheetVisibleChanged(false))

            is StoryReportAction.SelectReason -> emit(StoryReportChange.ReasonSelected(action.reason))

            is StoryReportAction.ChangeDetail -> emit(StoryReportChange.DetailChanged(action.detail))

            StoryReportAction.Submit -> submit(storyId, state)
        }
    }

    private suspend fun submit(
        storyId: String?,
        state: StoryReportUiState,
    ) {
        val target = storyId ?: return
        val reason = state.reason ?: return
        if (submitJob?.isActive == true) return
        emit(StoryReportChange.SubmitRequested)
        analytics.track(AnalyticsEvent.ReportSubmitted(target, reason.name, hasDetail = state.detail.isNotBlank()))
        submitJob =
            scope.launch {
                when (val result = repository.reportStory(target, reason, state.detail)) {
                    is DomainResult.Success -> {
                        // 접수되면 닫는다 — 같은 스토리를 다시 신고할 이유가 없다.
                        emit(StoryReportChange.SheetVisibleChanged(false))
                        notify(true)
                    }

                    is DomainResult.Failure -> {
                        analytics.track(AnalyticsEvent.ReportFailed(result.error::class.simpleName.orEmpty()))
                        // 시트는 열어 둔다 — 쓰던 사유·상세를 다시 입력하게 하지 않는다.
                        emit(StoryReportChange.SubmitFailed)
                        notify(false)
                    }
                }
            }
    }
}
