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

sealed interface CreditChargeIntent {
    /** 화면이 보일 때. 첫 페이지 조회와 잔액 갱신을 잇는다. */
    data object ScreenShown : CreditChargeIntent

    data object Retry : CreditChargeIntent

    /** 내역 탭의 당겨서 새로고침. 잔액과 첫 페이지를 다시 읽는다. */
    data object Refresh : CreditChargeIntent

    /** 무료 충전 탭의 당겨서 새로고침. 프로필만 다시 읽어 자정이 지난 출석 여부와 잔액을 맞춘다. */
    data object RefreshProfile : CreditChargeIntent

    /** 목록 끝에 닿았다. 다음 커서가 없거나 이미 받고 있으면 아무 일도 하지 않는다. */
    data object LoadMore : CreditChargeIntent

    data object ClaimAttendance : CreditChargeIntent
}

data class CreditChargeUiState(
    /** 잔액 정본은 프로필이다. 아직 못 읽었으면 null 이라 골격을 그린다. */
    val balance: Long? = null,
    /** 오늘 출석했는지. 프로필을 아직 못 읽었으면 null 이라 버튼을 눌러도 되는지 알 수 없다. */
    val attendedToday: Boolean? = null,
    val isClaimingAttendance: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val items: List<CreditTransaction> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isRefreshing: Boolean = false,
    /** 무료 충전 탭을 당겨 프로필을 다시 읽는 중. 내역 탭의 [isRefreshing] 과 표시자가 따로 논다. */
    val isRefreshingProfile: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null

    /** 프로필을 읽었고 오늘 아직 안 받았고 요청이 진행 중이 아닐 때만 누를 수 있다. */
    val canClaimAttendance: Boolean get() = attendedToday == false && !isClaimingAttendance
}

sealed interface CreditChargeEvent {
    data class ProfileChanged(
        val balance: Long?,
        val attendedToday: Boolean?,
    ) : CreditChargeEvent

    data object AttendanceStarted : CreditChargeEvent

    data object AttendanceFinished : CreditChargeEvent

    data object LoadStarted : CreditChargeEvent

    data class Loaded(
        val page: CreditTransactionPage,
    ) : CreditChargeEvent

    data object LoadFailed : CreditChargeEvent

    data object RefreshStarted : CreditChargeEvent

    data object RefreshFailed : CreditChargeEvent

    data object ProfileRefreshStarted : CreditChargeEvent

    data object ProfileRefreshFinished : CreditChargeEvent

    data object LoadMoreStarted : CreditChargeEvent

    data class MoreLoaded(
        val page: CreditTransactionPage,
    ) : CreditChargeEvent

    data object LoadMoreFailed : CreditChargeEvent
}

sealed interface CreditChargeEffect {
    /** 새로고침 실패. 보고 있던 목록은 그대로 두고 토스트로만 알린다. */
    data object ShowRefreshFailed : CreditChargeEffect

    data class AttendanceRewarded(
        val amount: Long,
    ) : CreditChargeEffect

    data object AttendanceAlreadyDone : CreditChargeEffect

    data object AttendanceFailed : CreditChargeEffect
}

/**
 * 이프 충전. 무료 충전(출석)과 내역 두 탭을 한 화면 상태로 든다.
 *
 * 잔액과 목록의 출처가 다르다 — 잔액은 프로필(`GET /auth/me`)이 정본이고 목록은 원장이다.
 * 목록 금액을 합산해 잔액을 만들지 않는다: 구매는 목록에서 빠지고 만료 회수는 실제 만료보다
 * 늦게 기록돼 합계가 잔액과 어긋난다.
 */
@HiltViewModel
class CreditChargeViewModel
    @Inject
    constructor(
        private val creditRepository: CreditRepository,
        private val userProfileRepository: UserProfileRepository,
    ) : MviViewModel<CreditChargeIntent, CreditChargeUiState, CreditChargeEvent, CreditChargeEffect>(
            CreditChargeUiState(),
        ) {
        /** 마지막으로 프로필을 다시 읽은 시점. 없으면 아직 한 번도 읽지 않았다. */
        private var lastRefreshMark: TimeSource.Monotonic.ValueTimeMark? = null

        init {
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile ->
                    dispatchEvent(
                        CreditChargeEvent.ProfileChanged(
                            balance = profile?.creditBalance,
                            attendedToday = profile?.attendedToday,
                        ),
                    )
                }
            }
        }

        override suspend fun handleIntent(intent: CreditChargeIntent) {
            when (intent) {
                CreditChargeIntent.ScreenShown -> {
                    refreshBalanceIfStale()
                    if (uiState.value.items.isEmpty()) loadFirstPage()
                }

                CreditChargeIntent.Retry -> loadFirstPage()
                CreditChargeIntent.Refresh -> refresh()
                CreditChargeIntent.RefreshProfile -> refreshProfile()
                CreditChargeIntent.LoadMore -> loadNextPage()
                CreditChargeIntent.ClaimAttendance -> claimAttendance()
            }
        }

        override fun reduce(
            state: CreditChargeUiState,
            event: CreditChargeEvent,
        ): CreditChargeUiState =
            when (event) {
                is CreditChargeEvent.ProfileChanged ->
                    state.copy(balance = event.balance, attendedToday = event.attendedToday)

                CreditChargeEvent.AttendanceStarted -> state.copy(isClaimingAttendance = true)
                CreditChargeEvent.AttendanceFinished -> state.copy(isClaimingAttendance = false)
                CreditChargeEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false)
                is CreditChargeEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        loadFailed = false,
                        items = event.page.items,
                        nextCursor = event.page.nextCursor,
                        loadMoreFailed = false,
                        isRefreshing = false,
                    )

                CreditChargeEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)
                CreditChargeEvent.RefreshStarted -> state.copy(isRefreshing = true)
                // 새로고침 실패는 보고 있던 목록을 건드리지 않는다 — 알림은 토스트가 맡는다.
                CreditChargeEvent.RefreshFailed -> state.copy(isRefreshing = false)
                CreditChargeEvent.ProfileRefreshStarted -> state.copy(isRefreshingProfile = true)
                CreditChargeEvent.ProfileRefreshFinished -> state.copy(isRefreshingProfile = false)
                CreditChargeEvent.LoadMoreStarted -> state.copy(isLoadingMore = true, loadMoreFailed = false)
                is CreditChargeEvent.MoreLoaded ->
                    state.copy(
                        isLoadingMore = false,
                        items = state.items + event.page.items,
                        nextCursor = event.page.nextCursor,
                    )
                // 이미 그린 목록은 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
                CreditChargeEvent.LoadMoreFailed -> state.copy(isLoadingMore = false, loadMoreFailed = true)
            }

        /**
         * 당겨서 새로고침. 골격을 다시 깔지 않고 보던 목록 위에서 갱신하며, 잔액도 간격을 보지 않고
         * 다시 읽는다 — 사용자가 명시적으로 요청한 갱신이라 방금 읽었다는 이유로 건너뛰면 안 된다.
         */
        private suspend fun refresh() {
            if (uiState.value.isRefreshing) return
            dispatchEvent(CreditChargeEvent.RefreshStarted)
            lastRefreshMark = TimeSource.Monotonic.markNow()
            userProfileRepository.refresh()
            when (val result = creditRepository.getTransactions()) {
                is DomainResult.Success -> dispatchEvent(CreditChargeEvent.Loaded(result.value))
                is DomainResult.Failure -> {
                    dispatchEvent(CreditChargeEvent.RefreshFailed)
                    dispatchEffect(CreditChargeEffect.ShowRefreshFailed)
                }
            }
        }

        /**
         * 무료 충전 탭의 당겨서 새로고침. 출석 여부는 자정에 서버에서 초기화되는데 화면을 켜 둔 채
         * 자정을 넘기면 프로필이 어제 값이라 버튼이 "출석 완료"로 잠겨 있다 — 화면을 나가지 않고
         * 맞출 수단이다. 내역은 이 탭이 그리지 않으므로 읽지 않는다. 명시적 요청이라 간격을 보지 않는다.
         */
        private suspend fun refreshProfile() {
            if (uiState.value.isRefreshingProfile) return
            dispatchEvent(CreditChargeEvent.ProfileRefreshStarted)
            lastRefreshMark = TimeSource.Monotonic.markNow()
            if (userProfileRepository.refresh() is DomainResult.Failure) {
                dispatchEffect(CreditChargeEffect.ShowRefreshFailed)
            }
            dispatchEvent(CreditChargeEvent.ProfileRefreshFinished)
        }

        private suspend fun loadFirstPage() {
            dispatchEvent(CreditChargeEvent.LoadStarted)
            when (val result = creditRepository.getTransactions()) {
                is DomainResult.Success -> dispatchEvent(CreditChargeEvent.Loaded(result.value))
                is DomainResult.Failure -> dispatchEvent(CreditChargeEvent.LoadFailed)
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
            dispatchEvent(CreditChargeEvent.LoadMoreStarted)
            when (val result = creditRepository.getTransactions(cursor)) {
                is DomainResult.Success -> dispatchEvent(CreditChargeEvent.MoreLoaded(result.value))
                is DomainResult.Failure -> dispatchEvent(CreditChargeEvent.LoadMoreFailed)
            }
        }

        /**
         * 출석 보상. 지급 결과를 잔액에 직접 더하지 않고 프로필을 다시 읽는다 —
         * 잔액·출석 여부의 정본은 프로필이고 화면이 그것을 복제하지 않는다.
         */
        private suspend fun claimAttendance() {
            if (uiState.value.isClaimingAttendance) return
            dispatchEvent(CreditChargeEvent.AttendanceStarted)
            when (val result = creditRepository.claimAttendance()) {
                is DomainResult.Success -> {
                    if (result.value.rewarded) {
                        dispatchEffect(CreditChargeEffect.AttendanceRewarded(result.value.amount ?: 0))
                    } else {
                        dispatchEffect(CreditChargeEffect.AttendanceAlreadyDone)
                    }
                    // 방금 읽었더라도 지급 결과는 반드시 반영해야 하므로 간격을 보지 않는다.
                    lastRefreshMark = TimeSource.Monotonic.markNow()
                    userProfileRepository.refresh()
                }

                is DomainResult.Failure -> dispatchEffect(CreditChargeEffect.AttendanceFailed)
            }
            dispatchEvent(CreditChargeEvent.AttendanceFinished)
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
