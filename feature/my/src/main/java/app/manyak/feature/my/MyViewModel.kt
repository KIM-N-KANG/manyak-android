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
        init {
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile -> dispatchEvent(MyEvent.ProfileChanged(profile)) }
            }
            viewModelScope.launch {
                themePreferenceRepository.themeMode.collect { mode -> dispatchEvent(MyEvent.ThemeModeChanged(mode)) }
            }
            // 잔액·출석 여부는 다른 화면에서 바뀔 수 있어 진입 시 한 번 새로 읽는다.
            // 화면(LaunchedEffect)이 아니라 여기서 시작해 구성 변경마다 반복되지 않게 한다.
            onIntent(MyIntent.Refresh)
        }

        override suspend fun handleIntent(intent: MyIntent) {
            when (intent) {
                MyIntent.LogOut -> logOut()
                // 실패는 캐시된 값을 그대로 두는 것으로 흡수한다. 세션 상태를 바꾸지 않는다.
                MyIntent.Refresh -> userProfileRepository.refresh()
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
                    userProfileRepository.refresh()
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
    }
