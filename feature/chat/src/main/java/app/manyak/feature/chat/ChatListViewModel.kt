package app.manyak.feature.chat

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatSummary
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.ui.mvi.MviViewModel
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
)

sealed interface ChatListIntent {
    /** 화면이 다시 보였다. 떠난 사이 바뀐 목록을 서버와 맞춘다. */
    data object ScreenShown : ChatListIntent

    /** 목록 조회 실패 화면의 다시 시도. */
    data object Retry : ChatListIntent

    /** 목록을 당겨서 새로고침. */
    data object Refresh : ChatListIntent
}

sealed interface ChatListEvent {
    data object LoadStarted : ChatListEvent

    data object RefreshStarted : ChatListEvent

    data class ChatsLoaded(
        val chats: List<ChatSummary>,
    ) : ChatListEvent

    data object LoadFailed : ChatListEvent

    data object RefreshFailed : ChatListEvent
}

sealed interface ChatListEffect {
    data object ShowRefreshFailed : ChatListEffect
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
 */
@HiltViewModel
class ChatListViewModel
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
    ) : MviViewModel<ChatListIntent, ChatListUiState, ChatListEvent, ChatListEffect>(ChatListUiState()) {
        private var loadJob: Job? = null

        override suspend fun handleIntent(intent: ChatListIntent) {
            when (intent) {
                // 이미 그릴 목록이 있으면 갱신이 보이지 않아야 한다 — 골격이 다시 깔리면 복귀가 재진입처럼 보인다.
                ChatListIntent.ScreenShown ->
                    load(if (uiState.value.chats.isEmpty()) LoadKind.Blocking else LoadKind.Silent)

                ChatListIntent.Retry -> load(LoadKind.Blocking)

                ChatListIntent.Refresh -> load(LoadKind.Refresh)
            }
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
