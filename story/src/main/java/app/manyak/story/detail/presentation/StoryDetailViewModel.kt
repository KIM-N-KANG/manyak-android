package app.manyak.story.detail.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.ReportSource
import app.manyak.common.domain.chat.ChatStarter
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryDeletion
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.report.domain.ReportRepository
import app.manyak.report.presentation.StoryReportAction
import app.manyak.report.presentation.StoryReportChange
import app.manyak.report.presentation.StoryReportController
import app.manyak.report.presentation.StoryReportUiState
import app.manyak.report.presentation.reduceReport
import app.manyak.story.domain.StoryRepository
import app.manyak.story.entity.StoryDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 조회 실패의 종류. 재시도로 나아지는지가 갈려 화면이 상태 코드를 직접 보지 않게 한다. */
enum class StoryDetailLoadError {
    /** 없거나 읽을 수 없는 스토리(404). 같은 요청의 결과가 달라지지 않아 재시도를 두지 않는다. */
    NOT_FOUND,

    /** 그 밖의 실패. 재시도로 복구할 수 있다. */
    GENERAL,
}

data class StoryDetailUiState(
    val isLoading: Boolean = true,
    val story: StoryDetail? = null,
    val loadError: StoryDetailLoadError? = null,
    /** 시작 설정이 둘 이상일 때 고른 것. 시작 설정이 없으면 null 이고 채팅 시작이 서버 폴백을 쓴다. */
    val selectedStartSettingId: String? = null,
    val isImageViewerOpen: Boolean = false,
    val isStartingChat: Boolean = false,
    val startChatFailed: Boolean = false,
    val report: StoryReportUiState = StoryReportUiState(),
    /** 삭제 확인 다이얼로그. 내 스토리로 들어온 상세에서만 열린다. */
    val isDeleteDialogOpen: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val selectedStartSetting
        get() = story?.startSettings?.firstOrNull { setting -> setting.id == selectedStartSettingId }
}

sealed interface StoryDetailIntent {
    /** 화면이 보였다. 조회하고, 채팅방에서 돌아온 경우라면 시작 버튼 잠금도 푼다. */
    data object ScreenShown : StoryDetailIntent

    data object Retry : StoryDetailIntent

    data object OpenImageViewer : StoryDetailIntent

    data object CloseImageViewer : StoryDetailIntent

    data class SelectStartSetting(
        val startSettingId: String,
    ) : StoryDetailIntent

    data object StartChat : StoryDetailIntent

    data class Report(
        val action: StoryReportAction,
    ) : StoryDetailIntent

    /** 헤더 메뉴의 "삭제하기" — 바로 지우지 않고 확인을 묻는다. */
    data object RequestDelete : StoryDetailIntent

    data object ConfirmDelete : StoryDetailIntent

    data object DismissDeleteDialog : StoryDetailIntent
}

sealed interface StoryDetailEvent {
    data object LoadStarted : StoryDetailEvent

    data class Loaded(
        val story: StoryDetail,
        val selectedStartSettingId: String?,
    ) : StoryDetailEvent

    data class LoadFailed(
        val error: StoryDetailLoadError,
    ) : StoryDetailEvent

    data class ImageViewerVisibleChanged(
        val visible: Boolean,
    ) : StoryDetailEvent

    data class StartSettingSelected(
        val startSettingId: String,
    ) : StoryDetailEvent

    data object ChatStartRequested : StoryDetailEvent

    data object ChatStartFailed : StoryDetailEvent

    /** 채팅방에서 돌아왔다. 성공 뒤 남겨 둔 시작 잠금을 걷는다. */
    data object ChatStartReset : StoryDetailEvent

    data class Report(
        val change: StoryReportChange,
    ) : StoryDetailEvent

    data class DeleteDialogVisibleChanged(
        val visible: Boolean,
    ) : StoryDetailEvent

    data object DeleteStarted : StoryDetailEvent

    data object DeleteFailed : StoryDetailEvent
}

sealed interface StoryDetailEffect {
    data class NavigateToChat(
        val chatId: String,
    ) : StoryDetailEffect

    data object ShowReportSubmitted : StoryDetailEffect

    data object ShowReportFailed : StoryDetailEffect

    /** 삭제됐다 — 더 볼 것이 없으니 화면이 진입한 목록으로 돌아간다. */
    data object StoryDeleted : StoryDetailEffect

    data object ShowDeleteFailed : StoryDetailEffect
}

/**
 * 스토리 상세. 셸 없는 전체 화면이며 홈·제작 목록의 카드 탭으로 들어온다.
 *
 * 조회 시점은 화면이 보일 때마다다 — 채팅방에서 뒤로가기로 돌아오는 자리라 플레이한 만큼 턴 수가
 * 늘고 본 엔딩이 새로 붙는다. 이미 그릴 본문이 있는 갱신은 골격도 실패 화면도 띄우지 않는다.
 *
 뷰어의 열림 상태를 여기에 두는 이유는 회전에는 살아남고 프로세스 사망에는 사라져야 하기
 * 때문이다 — 화면 로컬 `remember` 는 회전에서 닫히고, 저장 상태는 프로세스 재시작까지 살아남는다.
 * 시작 상황 셀렉트의 펼침은 그 컨트롤이 직접 든다 — 화면 밖에서 알 필요가 없는 표현 상태다.
 */
@HiltViewModel(assistedFactory = StoryDetailViewModel.Factory::class)
class StoryDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val storyId: String,
        private val storyRepository: StoryRepository,
        private val chatRepository: ChatStarter,
        private val analytics: Analytics,
        reportRepository: ReportRepository,
        private val storyDeletion: StoryDeletion,
    ) : MviViewModel<StoryDetailIntent, StoryDetailUiState, StoryDetailEvent, StoryDetailEffect>(
            StoryDetailUiState(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(storyId: String): StoryDetailViewModel
        }

        private var loadJob: Job? = null
        private var startChatJob: Job? = null
        private var deleteJob: Job? = null

        /**
         * 고른 시작 설정의 장부. UiState 가 아니라 여기서 읽는 이유는 상태 반영이 이벤트 채널을 거쳐
         * 한 박자 늦어서다 — 고른 직후 채팅 시작이 들어오면 UiState 에는 아직 이전 선택이 있다.
         */
        private var selectedStartSettingId: String? = null

        /** 신고 절차는 채팅방과 같아 공유 신고 컨트롤러가 소유한다. */
        private val report =
            StoryReportController(
                scope = viewModelScope,
                repository = reportRepository,
                analytics = analytics,
                source = ReportSource.STORY_DETAIL,
                emit = { change -> dispatchEvent(StoryDetailEvent.Report(change)) },
                notify = { submitted ->
                    dispatchEffect(
                        if (submitted) {
                            StoryDetailEffect.ShowReportSubmitted
                        } else {
                            StoryDetailEffect.ShowReportFailed
                        },
                    )
                },
            )

        init {
            analytics.track(AnalyticsEvent.StoryDetailViewed(storyId))
        }

        override suspend fun handleIntent(intent: StoryDetailIntent) {
            val state = uiState.value
            when (intent) {
                StoryDetailIntent.ScreenShown -> {
                    // 성공 직후에 풀면 화면이 사라지는 중에 버튼이 되살아나 깜빡인다. 그래서 복귀 시점에 푼다.
                    // 요청이 아직 살아 있으면 풀지 않는다 — 구성 변경으로 화면만 다시 만들어졌을 때
                    // 잠금과 스피너가 사라지면 진행 중인 생성이 없는 것처럼 보이고, 다시 눌러도 반응이 없다.
                    if (startChatJob?.isActive != true) dispatchEvent(StoryDetailEvent.ChatStartReset)
                    load(showProgress = state.story == null)
                }

                StoryDetailIntent.Retry -> load(showProgress = true)

                StoryDetailIntent.OpenImageViewer ->
                    // 열 이미지가 없으면 빈 화면이 뜬다.
                    if (state.story?.thumbnailUrl != null) {
                        analytics.track(AnalyticsEvent.ThumbnailClicked(storyId))
                        dispatchEvent(StoryDetailEvent.ImageViewerVisibleChanged(visible = true))
                    }

                StoryDetailIntent.CloseImageViewer ->
                    dispatchEvent(StoryDetailEvent.ImageViewerVisibleChanged(visible = false))

                is StoryDetailIntent.SelectStartSetting -> {
                    analytics.track(AnalyticsEvent.StartSettingSelected(storyId, intent.startSettingId))
                    selectedStartSettingId = intent.startSettingId
                    dispatchEvent(StoryDetailEvent.StartSettingSelected(intent.startSettingId))
                }

                StoryDetailIntent.StartChat -> startChat(state)

                is StoryDetailIntent.Report -> report.handle(intent.action, state.story?.id, state.report)

                StoryDetailIntent.RequestDelete ->
                    dispatchEvent(StoryDetailEvent.DeleteDialogVisibleChanged(visible = true))

                StoryDetailIntent.ConfirmDelete -> delete()

                // 삭제가 진행 중이면 닫지 않는다 — 결과가 정해진 뒤 상태 전이가 닫는다.
                StoryDetailIntent.DismissDeleteDialog ->
                    if (deleteJob?.isActive != true) {
                        dispatchEvent(StoryDetailEvent.DeleteDialogVisibleChanged(visible = false))
                    }
            }
        }

        /**
         * @param showProgress 그릴 본문이 없을 때만 true. 본문이 있는 갱신은 골격도 실패 화면도 띄우지
         *  않는다 — 보고 있던 본문이 사라지는 쪽이 갱신 실패보다 나쁘다.
         */
        private fun load(showProgress: Boolean) {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    if (showProgress) dispatchEvent(StoryDetailEvent.LoadStarted)
                    when (val result = storyRepository.storyDetail(storyId)) {
                        is DomainResult.Success -> {
                            val story = result.value
                            selectedStartSettingId = story.selectStartSetting(selectedStartSettingId)
                            dispatchEvent(StoryDetailEvent.Loaded(story, selectedStartSettingId))
                        }

                        is DomainResult.Failure ->
                            if (showProgress) dispatchEvent(StoryDetailEvent.LoadFailed(result.error.toLoadError()))
                    }
                }
        }

        private suspend fun startChat(state: StoryDetailUiState) {
            val story = state.story ?: return
            if (startChatJob?.isActive == true) return
            analytics.track(AnalyticsEvent.ChatStartButtonClicked(story.id))
            dispatchEvent(StoryDetailEvent.ChatStartRequested)
            startChatJob =
                viewModelScope.launch {
                    val result = chatRepository.createChat(story.id, selectedStartSettingId)
                    when (result) {
                        // 잠금은 여기서 풀지 않는다 — 복귀 시 ScreenShown 이 푼다.
                        is DomainResult.Success ->
                            dispatchEffect(StoryDetailEffect.NavigateToChat(result.value.id))

                        is DomainResult.Failure -> dispatchEvent(StoryDetailEvent.ChatStartFailed)
                    }
                }
        }

        /** 삭제 성공 뒤 다이얼로그는 걷지 않는다 — 화면이 곧 사라지므로 걷으면 본문이 잠깐 되살아난다. */
        private fun delete() {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(StoryDetailEvent.DeleteStarted)
                    when (storyDeletion.deleteStory(storyId)) {
                        is DomainResult.Success -> {
                            analytics.track(AnalyticsEvent.StoryDetailStoryDeleted(storyId))
                            dispatchEffect(StoryDetailEffect.StoryDeleted)
                        }

                        is DomainResult.Failure -> {
                            dispatchEvent(StoryDetailEvent.DeleteFailed)
                            dispatchEffect(StoryDetailEffect.ShowDeleteFailed)
                        }
                    }
                }
        }

        override fun reduce(
            state: StoryDetailUiState,
            event: StoryDetailEvent,
        ): StoryDetailUiState =
            when (event) {
                StoryDetailEvent.LoadStarted -> state.copy(isLoading = true, loadError = null)

                is StoryDetailEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        story = event.story,
                        loadError = null,
                        selectedStartSettingId = event.selectedStartSettingId,
                        // 갱신으로 썸네일이 사라졌으면 열려 있던 뷰어도 닫는다.
                        isImageViewerOpen = state.isImageViewerOpen && event.story.thumbnailUrl != null,
                    )

                is StoryDetailEvent.LoadFailed ->
                    state.copy(isLoading = false, story = null, loadError = event.error)

                is StoryDetailEvent.ImageViewerVisibleChanged -> state.copy(isImageViewerOpen = event.visible)

                is StoryDetailEvent.StartSettingSelected ->
                    state.copy(selectedStartSettingId = event.startSettingId)

                StoryDetailEvent.ChatStartRequested -> state.copy(isStartingChat = true, startChatFailed = false)

                StoryDetailEvent.ChatStartFailed -> state.copy(isStartingChat = false, startChatFailed = true)

                // 실패 문구는 남긴다 — 화면이 다시 만들어졌다고 해서 사용자가 읽지 않은 실패가 없던 일이 되지는 않는다.
                // 다시 누르면 ChatStartRequested 가 지운다.
                StoryDetailEvent.ChatStartReset -> state.copy(isStartingChat = false)

                is StoryDetailEvent.Report -> state.copy(report = state.report.reduceReport(event.change))

                is StoryDetailEvent.DeleteDialogVisibleChanged -> state.copy(isDeleteDialogOpen = event.visible)

                StoryDetailEvent.DeleteStarted -> state.copy(isDeleting = true)

                StoryDetailEvent.DeleteFailed -> state.copy(isDeleting = false, isDeleteDialogOpen = false)
            }
    }

/**
 * 갱신 전에 고른 시작 설정이 아직 있으면 그대로 두고, 사라졌거나 처음이면 첫 번째를 고른다.
 * 서버가 시작 설정을 주지 않으면 null 이고 채팅 시작이 서버 폴백을 쓴다.
 */
private fun StoryDetail.selectStartSetting(currentId: String?): String? =
    startSettings.firstOrNull { setting -> setting.id == currentId }?.id ?: startSettings.firstOrNull()?.id

private fun DomainError.toLoadError(): StoryDetailLoadError =
    if (this is DomainError.Server && status == HTTP_NOT_FOUND) {
        StoryDetailLoadError.NOT_FOUND
    } else {
        StoryDetailLoadError.GENERAL
    }

private const val HTTP_NOT_FOUND = 404
