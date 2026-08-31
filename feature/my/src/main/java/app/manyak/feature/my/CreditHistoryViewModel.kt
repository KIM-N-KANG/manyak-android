package app.manyak.feature.my

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.credit.CreditTransaction
import app.manyak.core.domain.credit.CreditTransactionPage
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.user.UserProfileRepository
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

sealed interface CreditHistoryIntent {
    /** 화면이 보일 때. 첫 페이지 조회와 잔액 갱신을 잇는다. */
    data object ScreenShown : CreditHistoryIntent

    data object Retry : CreditHistoryIntent

    /** 목록 끝에 닿았다. 다음 커서가 없거나 이미 받고 있으면 아무 일도 하지 않는다. */
    data object LoadMore : CreditHistoryIntent
}

data class CreditHistoryUiState(
    /** 잔액 정본은 프로필이다. 아직 못 읽었으면 null 이라 골격을 그린다. */
    val balance: Long? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val items: List<CreditTransaction> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null
}

sealed interface CreditHistoryEvent {
    data class BalanceChanged(
        val balance: Long?,
    ) : CreditHistoryEvent

    data object LoadStarted : CreditHistoryEvent

    data class Loaded(
        val page: CreditTransactionPage,
    ) : CreditHistoryEvent

    data object LoadFailed : CreditHistoryEvent

    data object LoadMoreStarted : CreditHistoryEvent

    data class MoreLoaded(
        val page: CreditTransactionPage,
    ) : CreditHistoryEvent

    data object LoadMoreFailed : CreditHistoryEvent
}

/**
 * 이프 내역.
 *
 * 잔액과 목록의 출처가 다르다 — 잔액은 프로필(`GET /auth/me`)이 정본이고 목록은 원장이다.
 * 목록 금액을 합산해 잔액을 만들지 않는다: 구매는 목록에서 빠지고 만료 회수는 실제 만료보다
 * 늦게 기록돼 합계가 잔액과 어긋난다.
 */
@HiltViewModel
class CreditHistoryViewModel
    @Inject
    constructor(
        private val creditRepository: CreditRepository,
        private val userProfileRepository: UserProfileRepository,
    ) : MviViewModel<CreditHistoryIntent, CreditHistoryUiState, CreditHistoryEvent, Nothing>(
            CreditHistoryUiState(),
        ) {
        /** 마지막으로 프로필을 다시 읽은 시점. 없으면 아직 한 번도 읽지 않았다. */
        private var lastRefreshMark: TimeSource.Monotonic.ValueTimeMark? = null

        init {
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile ->
                    dispatchEvent(CreditHistoryEvent.BalanceChanged(profile?.creditBalance))
                }
            }
        }

        override suspend fun handleIntent(intent: CreditHistoryIntent) {
            when (intent) {
                CreditHistoryIntent.ScreenShown -> {
                    refreshBalanceIfStale()
                    if (uiState.value.items.isEmpty()) loadFirstPage()
                }

                CreditHistoryIntent.Retry -> loadFirstPage()
                CreditHistoryIntent.LoadMore -> loadNextPage()
            }
        }

        override fun reduce(
            state: CreditHistoryUiState,
            event: CreditHistoryEvent,
        ): CreditHistoryUiState =
            when (event) {
                is CreditHistoryEvent.BalanceChanged -> state.copy(balance = event.balance)
                CreditHistoryEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false)
                is CreditHistoryEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        loadFailed = false,
                        items = event.page.items,
                        nextCursor = event.page.nextCursor,
                        loadMoreFailed = false,
                    )

                CreditHistoryEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)
                CreditHistoryEvent.LoadMoreStarted -> state.copy(isLoadingMore = true, loadMoreFailed = false)
                is CreditHistoryEvent.MoreLoaded ->
                    state.copy(
                        isLoadingMore = false,
                        items = state.items + event.page.items,
                        nextCursor = event.page.nextCursor,
                    )
                // 이미 그린 목록은 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
                CreditHistoryEvent.LoadMoreFailed -> state.copy(isLoadingMore = false, loadMoreFailed = true)
            }

        private suspend fun loadFirstPage() {
            dispatchEvent(CreditHistoryEvent.LoadStarted)
            when (val result = creditRepository.getTransactions()) {
                is DomainResult.Success -> dispatchEvent(CreditHistoryEvent.Loaded(result.value))
                is DomainResult.Failure -> dispatchEvent(CreditHistoryEvent.LoadFailed)
            }
        }

        /**
         * 다음 페이지. 커서는 서버가 준 값을 그대로 되돌려 주기만 한다.
         *
         * 목록 끝은 스크롤 한 번에 여러 프레임 동안 참이라 진행 플래그로 중복 요청을 막는다.
         * 실패 뒤 자동 재요청을 멈추는 것은 화면이 맡는다 — 같은 실패를 스크롤마다 반복하지 않는다.
         */
        private suspend fun loadNextPage() {
            val state = uiState.value
            val cursor = state.nextCursor ?: return
            if (state.isLoading || state.isLoadingMore) return
            dispatchEvent(CreditHistoryEvent.LoadMoreStarted)
            when (val result = creditRepository.getTransactions(cursor)) {
                is DomainResult.Success -> dispatchEvent(CreditHistoryEvent.MoreLoaded(result.value))
                is DomainResult.Failure -> dispatchEvent(CreditHistoryEvent.LoadMoreFailed)
            }
        }

        /**
         * 잔액은 채팅·제작에서 바뀌므로 화면이 보일 때 다시 읽는다. 다만 같은 요청이 구성 변경으로도
         * 오므로 방금 읽었으면 건너뛴다(마이 탭과 같은 간격).
         */
        private suspend fun refreshBalanceIfStale() {
            val lastRefresh = lastRefreshMark
            if (lastRefresh != null && lastRefresh.elapsedNow() < REFRESH_MIN_INTERVAL) return
            lastRefreshMark = TimeSource.Monotonic.markNow()
            userProfileRepository.refresh()
        }

        private companion object {
            val REFRESH_MIN_INTERVAL = 5.seconds
        }
    }
