package app.manyak.feature.chat

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatSummary
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryRepository
import app.manyak.core.ui.mvi.MviViewModel
import app.manyak.core.ui.report.StoryReportAction
import app.manyak.core.ui.report.StoryReportChange
import app.manyak.core.ui.report.StoryReportController
import app.manyak.core.ui.report.StoryReportUiState
import app.manyak.core.ui.report.reduceReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val isLoading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 다시 읽는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
    /** 길게 눌러 옵션 시트를 연 카드. null 이면 시트가 없다. */
    val optionsTarget: ChatSummary? = null,
    /** 삭제 확인을 묻는 대상. null 이면 다이얼로그가 없다. */
    val deleteTarget: ChatSummary? = null,
    val isDeleting: Boolean = false,
    /** 신고 시트. 대상은 옵션 시트를 연 카드가 참조하는 스토리다. */
    val report: StoryReportUiState = StoryReportUiState(),
    /** 신고 시트가 열려 있는 동안의 대상 스토리. 옵션 시트가 닫혀도 신고가 어느 스토리인지 남아야 한다. */
    val reportStoryId: String? = null,
)

sealed interface ChatListIntent {
    /** 화면이 다시 보였다. 떠난 사이 바뀐 목록을 서버와 맞춘다. */
    data object ScreenShown : ChatListIntent

    /** 목록 조회 실패 화면의 다시 시도. */
    data object Retry : ChatListIntent

    /** 목록을 당겨서 새로고침. */
    data object Refresh : ChatListIntent

    /** 카드를 길게 눌렀다 — 신고·삭제를 담은 옵션 시트를 연다. */
    data class OpenOptions(
        val chat: ChatSummary,
    ) : ChatListIntent

    data object CloseOptions : ChatListIntent

    /** 옵션 시트의 "삭제하기" — 바로 지우지 않고 확인을 묻는다. */
    data object RequestDelete : ChatListIntent

    data object ConfirmDelete : ChatListIntent

    data object DismissDeleteDialog : ChatListIntent

    /** 옵션 시트의 "신고하기" 이후 신고 시트 안의 동작. */
    data class Report(
        val action: StoryReportAction,
    ) : ChatListIntent
}

sealed interface ChatListEvent {
    data object LoadStarted : ChatListEvent

    data object RefreshStarted : ChatListEvent

    data class ChatsLoaded(
        val chats: List<ChatSummary>,
    ) : ChatListEvent

    data object LoadFailed : ChatListEvent

    data object RefreshFailed : ChatListEvent

    data class OptionsTargetChanged(
        val chat: ChatSummary?,
    ) : ChatListEvent

    data class DeleteRequested(
        val chat: ChatSummary,
    ) : ChatListEvent

    data object DeleteDialogDismissed : ChatListEvent

    data object DeleteStarted : ChatListEvent

    data class DeleteSucceeded(
        val chatId: String,
    ) : ChatListEvent

    data object DeleteFailed : ChatListEvent

    data class ReportTargetChanged(
        val storyId: String?,
    ) : ChatListEvent

    data class Report(
        val change: StoryReportChange,
    ) : ChatListEvent
}

sealed interface ChatListEffect {
    data object ShowRefreshFailed : ChatListEffect

    data object ShowChatDeleted : ChatListEffect

    data object ShowChatDeleteFailed : ChatListEffect

    data object ShowReportSubmitted : ChatListEffect

    data object ShowReportFailed : ChatListEffect
}

/**
 * 채팅 탭. 진행 중인 채팅을 화면이 보일 때마다 조회한다.
 *
 * 세 목록 중 복귀 갱신이 가장 자주 의미를 갖는 자리다 — 채팅방은 이 화면 위가 아니라 셸 위에 쌓여
 * 돌아와도 상태가 그대로 살아 있는데, 그 사이 미리보기·턴 수·마지막 활동 시각이 모두 바뀌고 그
 * 채팅이 목록 맨 위로 올라온다. 이미 그릴 목록이 있는 갱신은 골격 없이 조용히 바꿔 끼우고,
 * 실패해도 보고 있던 목록을 지우지 않는다.
 *
 * 목록을 보는 중에도 서버와 맞출 수 있게 당겨서 새로고침을 둔다. 복귀 갱신과 달리 사용자가 명시적으로
 * 요청한 것이라 실패를 조용히 넘기지 않고 토스트로 알린다. 주기적 재조회는 두지 않는다.
 *
 * 카드를 길게 누르면 채팅방 옵션 메뉴와 같은 신고·삭제를 시트로 연다. 삭제 절차와 결과 문구는
 * 채팅방과 같고, 성공하면 재조회 없이 목록에서 그 카드만 뺀다.
 */
@HiltViewModel
class ChatListViewModel
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        storyRepository: StoryRepository,
    ) : MviViewModel<ChatListIntent, ChatListUiState, ChatListEvent, ChatListEffect>(ChatListUiState()) {
        private var loadJob: Job? = null
        private var deleteJob: Job? = null

        /** 신고 절차는 채팅방·상세와 같아 :core:ui 의 컨트롤러가 소유한다. */
        private val report =
            StoryReportController(
                scope = viewModelScope,
                repository = storyRepository,
                emit = { change -> dispatchEvent(ChatListEvent.Report(change)) },
                notify = { submitted ->
                    dispatchEffect(
                        if (submitted) ChatListEffect.ShowReportSubmitted else ChatListEffect.ShowReportFailed,
                    )
                },
            )

        override suspend fun handleIntent(intent: ChatListIntent) {
            val state = uiState.value
            when (intent) {
                // 이미 그릴 목록이 있으면 갱신이 보이지 않아야 한다 — 골격이 다시 깔리면 복귀가 재진입처럼 보인다.
                ChatListIntent.ScreenShown ->
                    load(if (state.chats.isEmpty()) LoadKind.Blocking else LoadKind.Silent)

                ChatListIntent.Retry -> load(LoadKind.Blocking)

                ChatListIntent.Refresh -> load(LoadKind.Refresh)

                is ChatListIntent.OpenOptions -> dispatchEvent(ChatListEvent.OptionsTargetChanged(intent.chat))

                ChatListIntent.CloseOptions -> dispatchEvent(ChatListEvent.OptionsTargetChanged(null))

                ChatListIntent.RequestDelete ->
                    state.optionsTarget?.let { chat ->
                        dispatchEvent(ChatListEvent.OptionsTargetChanged(null))
                        dispatchEvent(ChatListEvent.DeleteRequested(chat))
                    }

                ChatListIntent.ConfirmDelete -> state.deleteTarget?.let { chat -> delete(chat) }

                // 삭제가 진행 중이면 닫지 않는다 — 결과가 정해진 뒤 상태 전이가 닫는다.
                ChatListIntent.DismissDeleteDialog ->
                    if (deleteJob?.isActive != true) dispatchEvent(ChatListEvent.DeleteDialogDismissed)

                is ChatListIntent.Report -> handleReport(intent.action, state)
            }
        }

        /**
         * 신고 대상은 옵션 시트를 연 카드의 스토리다. 열 때 대상을 따로 적어 두는 이유는 옵션 시트가
         * 닫힌 뒤에도 신고 시트가 어느 스토리를 보내는지 알아야 해서다.
         */
        private suspend fun handleReport(
            action: StoryReportAction,
            state: ChatListUiState,
        ) {
            val storyId =
                if (action == StoryReportAction.Open) {
                    val target = state.optionsTarget?.storyId?.takeIf { id -> id.isNotBlank() } ?: return
                    dispatchEvent(ChatListEvent.OptionsTargetChanged(null))
                    dispatchEvent(ChatListEvent.ReportTargetChanged(target))
                    target
                } else {
                    state.reportStoryId
                }
            report.handle(action, storyId, state.report)
        }

        private fun load(kind: LoadKind) {
            // 명시적 요청인 새로고침은 진행 중인 조회를 기다리지 않고 취소한 뒤 시작한다.
            if (kind == LoadKind.Refresh) {
                loadJob?.cancel()
            } else if (loadJob?.isActive == true) {
                return
            }
            loadJob =
                viewModelScope.launch {
                    when (kind) {
                        LoadKind.Blocking -> dispatchEvent(ChatListEvent.LoadStarted)
                        LoadKind.Refresh -> dispatchEvent(ChatListEvent.RefreshStarted)
                        LoadKind.Silent -> Unit
                    }
                    when (val result = chatRepository.myChats()) {
                        is DomainResult.Success -> dispatchEvent(ChatListEvent.ChatsLoaded(result.value))
                        is DomainResult.Failure -> reportLoadFailure(kind)
                    }
                }
        }

        private suspend fun reportLoadFailure(kind: LoadKind) {
            when (kind) {
                LoadKind.Blocking -> dispatchEvent(ChatListEvent.LoadFailed)

                LoadKind.Refresh -> {
                    dispatchEvent(ChatListEvent.RefreshFailed)
                    dispatchEffect(ChatListEffect.ShowRefreshFailed)
                }

                LoadKind.Silent -> Unit
            }
        }

        private fun delete(target: ChatSummary) {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(ChatListEvent.DeleteStarted)
                    when (chatRepository.deleteChat(target.id)) {
                        is DomainResult.Success -> {
                            dispatchEvent(ChatListEvent.DeleteSucceeded(target.id))
                            dispatchEffect(ChatListEffect.ShowChatDeleted)
                        }

                        is DomainResult.Failure -> {
                            dispatchEvent(ChatListEvent.DeleteFailed)
                            dispatchEffect(ChatListEffect.ShowChatDeleteFailed)
                        }
                    }
                }
        }

        override fun reduce(
            state: ChatListUiState,
            event: ChatListEvent,
        ): ChatListUiState =
            when (event) {
                ChatListEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false, isRefreshing = false)

                ChatListEvent.RefreshStarted -> state.copy(isRefreshing = true)

                // 서버 응답 순서(최근 활동순)를 그대로 그린다.
                is ChatListEvent.ChatsLoaded ->
                    state.copy(
                        isLoading = false,
                        chats = event.chats,
                        loadFailed = false,
                        isRefreshing = false,
                    )

                ChatListEvent.LoadFailed ->
                    state.copy(isLoading = false, chats = emptyList(), loadFailed = true, isRefreshing = false)

                // 새로고침 실패는 보고 있던 목록을 건드리지 않는다 — 알림은 토스트가 맡는다.
                ChatListEvent.RefreshFailed -> state.copy(isRefreshing = false)

                is ChatListEvent.OptionsTargetChanged -> state.copy(optionsTarget = event.chat)

                is ChatListEvent.DeleteRequested -> state.copy(deleteTarget = event.chat)

                ChatListEvent.DeleteDialogDismissed -> state.copy(deleteTarget = null)

                ChatListEvent.DeleteStarted -> state.copy(isDeleting = true)

                // 서버 재조회 대신 로컬 제거로 목록을 맞춘다 — 서버가 지운 것을 다시 물을 이유가 없다.
                is ChatListEvent.DeleteSucceeded ->
                    state.copy(
                        isDeleting = false,
                        deleteTarget = null,
                        chats = state.chats.filterNot { chat -> chat.id == event.chatId },
                    )

                ChatListEvent.DeleteFailed -> state.copy(isDeleting = false, deleteTarget = null)

                is ChatListEvent.ReportTargetChanged -> state.copy(reportStoryId = event.storyId)

                is ChatListEvent.Report -> {
                    val report = state.report.reduceReport(event.change)
                    // 시트가 닫히면 대상도 함께 지운다 — 다음 신고가 지난 대상으로 나가면 안 된다.
                    state.copy(report = report, reportStoryId = state.reportStoryId.takeIf { report.isSheetOpen })
                }
            }
    }

/** 목록 조회를 부른 자리. 진행을 어떻게 보이고 실패를 어떻게 알릴지가 여기서 갈린다. */
private enum class LoadKind {
    /** 첫 조회·재시도. 골격을 깔고 실패하면 재시도 화면으로 바꾼다. */
    Blocking,

    /** 화면 복귀. 보고 있던 목록을 건드리지 않고, 실패해도 아무것도 알리지 않는다. */
    Silent,

    /** 당겨서 새로고침. 당김 표시자로 진행을 알리고 실패는 토스트로 알린다. */
    Refresh,
}
