package app.manyak.feature.my

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.credit.CreditRepository
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.settings.ThemeMode
import app.manyak.core.domain.settings.ThemePreferenceRepository
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

sealed interface MyIntent {
    data object LogOut : MyIntent

    data object Refresh : MyIntent

    data object ClaimAttendance : MyIntent

    data object CycleTheme : MyIntent
}

data class MyUiState(
    val profile: UserProfile? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isClaimingAttendance: Boolean = false,
    val isLoggingOut: Boolean = false,
)

sealed interface MyEvent {
    data class ProfileChanged(
        val profile: UserProfile?,
    ) : MyEvent

    data class ThemeModeChanged(
        val mode: ThemeMode,
    ) : MyEvent

    data object AttendanceStarted : MyEvent

    data object AttendanceFinished : MyEvent

    data object LogOutStarted : MyEvent
}

sealed interface MyEffect {
    data class AttendanceRewarded(
        val amount: Long,
    ) : MyEffect

    data object AttendanceAlreadyDone : MyEffect

    data object AttendanceFailed : MyEffect
}

/**
 * 마이 화면.
 *
 * 로그아웃은 화면이 사라져도 끝나야 하므로 Repository 구현이 앱 스코프에서 실행한다. 여기서는 진행
 * 상태만 표시하고, 완료 뒤 인증 그래프로의 전환은 루트가 세션 상태를 보고 결정한다.
 */
@HiltViewModel
class MyViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val userProfileRepository: UserProfileRepository,
        private val creditRepository: CreditRepository,
        private val themePreferenceRepository: ThemePreferenceRepository,
    ) : MviViewModel<MyIntent, MyUiState, MyEvent, MyEffect>(MyUiState()) {
        /** 마지막으로 프로필을 다시 읽은 시점. 없으면 아직 한 번도 읽지 않았다. */
        private var lastRefreshMark: TimeSource.Monotonic.ValueTimeMark? = null

        init {
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile -> dispatchEvent(MyEvent.ProfileChanged(profile)) }
            }
            viewModelScope.launch {
                themePreferenceRepository.themeMode.collect { mode -> dispatchEvent(MyEvent.ThemeModeChanged(mode)) }
            }
        }

        override suspend fun handleIntent(intent: MyIntent) {
            when (intent) {
                MyIntent.LogOut -> logOut()
                MyIntent.Refresh -> refreshProfileIfStale()
                MyIntent.ClaimAttendance -> claimAttendance()
                MyIntent.CycleTheme -> themePreferenceRepository.setThemeMode(uiState.value.themeMode.next())
            }
        }

        override fun reduce(
            state: MyUiState,
            event: MyEvent,
        ): MyUiState =
            when (event) {
                is MyEvent.ProfileChanged -> state.copy(profile = event.profile)
                is MyEvent.ThemeModeChanged -> state.copy(themeMode = event.mode)
                MyEvent.AttendanceStarted -> state.copy(isClaimingAttendance = true)
                MyEvent.AttendanceFinished -> state.copy(isClaimingAttendance = false)
                MyEvent.LogOutStarted -> state.copy(isLoggingOut = true)
            }

        /**
         * 화면이 다시 보일 때의 갱신.
         *
         * 잔액·출석 여부는 채팅·제작에서 바뀌므로 탭에 들어올 때마다 다시 읽어야 한다. ViewModel 은
         * 탭을 옮겨도 살아 있어 생성 시점에 한 번 읽는 것으로는 낡은 값이 남는다.
         *
         * 다만 같은 요청이 구성 변경(회전·다크 모드)으로도 오므로 방금 읽었으면 건너뛴다.
         */
        private suspend fun refreshProfileIfStale() {
            val lastRefresh = lastRefreshMark
            if (lastRefresh != null && lastRefresh.elapsedNow() < REFRESH_MIN_INTERVAL) return
            refreshProfile()
        }

        /** 실패는 캐시된 값을 그대로 두는 것으로 흡수한다. 세션 상태를 바꾸지 않는다. */
        private suspend fun refreshProfile() {
            lastRefreshMark = TimeSource.Monotonic.markNow()
            userProfileRepository.refresh()
        }

        private suspend fun claimAttendance() {
            if (uiState.value.isClaimingAttendance) return
            dispatchEvent(MyEvent.AttendanceStarted)
            when (val result = creditRepository.claimAttendance()) {
                is DomainResult.Success -> {
                    if (result.value.rewarded) {
                        dispatchEffect(MyEffect.AttendanceRewarded(result.value.amount ?: 0))
                    } else {
                        dispatchEffect(MyEffect.AttendanceAlreadyDone)
                    }
                    // 잔액·출석 여부는 프로필이 정본이라 지급 결과를 직접 더하지 않고 다시 읽는다.
                    // 방금 읽었더라도 지급 결과는 반드시 반영해야 하므로 간격을 보지 않는다.
                    refreshProfile()
                }

                is DomainResult.Failure -> dispatchEffect(MyEffect.AttendanceFailed)
            }
            dispatchEvent(MyEvent.AttendanceFinished)
        }

        private suspend fun logOut() {
            if (uiState.value.isLoggingOut) return
            dispatchEvent(MyEvent.LogOutStarted)
            sessionRepository.signOut()
        }

        private companion object {
            /** 구성 변경이 만드는 재요청을 흡수하는 최소 간격. 사람이 화면을 오가는 간격보다 짧다. */
            val REFRESH_MIN_INTERVAL = 5.seconds
        }
    }
