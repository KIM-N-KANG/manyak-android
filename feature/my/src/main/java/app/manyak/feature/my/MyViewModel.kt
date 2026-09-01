package app.manyak.feature.my

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.auth.AccountLinkRepository
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.settings.ThemeMode
import app.manyak.core.domain.settings.ThemePreferenceRepository
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

sealed interface MyIntent {
    data object LogOut : MyIntent

    data object Refresh : MyIntent

    data object CycleTheme : MyIntent

    /** 연동 확인 다이얼로그를 연다. 누르자마자 제공자 창을 열지 않는다. */
    data class RequestAccountLink(
        val target: AuthProvider,
    ) : MyIntent

    data object ConfirmAccountLink : MyIntent

    data object DismissAccountLink : MyIntent

    data object DismissLinkedToOtherUserNotice : MyIntent
}

data class MyUiState(
    val profile: UserProfile? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isLoggingOut: Boolean = false,
    /** 확인 다이얼로그가 떠 있는 대상 제공자. 없으면 다이얼로그가 닫혀 있다. */
    val accountLinkTarget: AuthProvider? = null,
    val isLinkingAccount: Boolean = false,
    val showsLinkedToOtherUserNotice: Boolean = false,
)

sealed interface MyEvent {
    data class ProfileChanged(
        val profile: UserProfile?,
    ) : MyEvent

    data class ThemeModeChanged(
        val mode: ThemeMode,
    ) : MyEvent

    data object LogOutStarted : MyEvent

    data class AccountLinkRequested(
        val target: AuthProvider,
    ) : MyEvent

    data object AccountLinkDismissed : MyEvent

    data object AccountLinkStarted : MyEvent

    data object AccountLinkFinished : MyEvent

    data object LinkedToOtherUserNoticed : MyEvent

    data object LinkedToOtherUserDismissed : MyEvent
}

sealed interface MyEffect {
    data class AccountLinked(
        val provider: AuthProvider,
    ) : MyEffect

    data object AccountAlreadyLinked : MyEffect

    data object AccountLinkFailed : MyEffect
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
        private val themePreferenceRepository: ThemePreferenceRepository,
        private val accountLinkRepository: AccountLinkRepository,
    ) : MviViewModel<MyIntent, MyUiState, MyEvent, MyEffect>(MyUiState()) {
        /** 마지막으로 프로필을 다시 읽은 시점. 없으면 아직 한 번도 읽지 않았다. */
        private var lastRefreshMark: TimeSource.Monotonic.ValueTimeMark? = null

        /**
         * 진행 중인 연동 작업. 상태 플래그는 리듀서를 한 번 거친 뒤에야 참이 되므로, 빠른 연속 확인이
         * 제공자 창을 두 번 여는 것은 이 참조로 막는다.
         */
        private var accountLinkJob: Job? = null

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
                MyIntent.CycleTheme -> themePreferenceRepository.setThemeMode(uiState.value.themeMode.next())
                is MyIntent.RequestAccountLink -> dispatchEvent(MyEvent.AccountLinkRequested(intent.target))
                MyIntent.ConfirmAccountLink -> startAccountLink()
                MyIntent.DismissAccountLink -> dispatchEvent(MyEvent.AccountLinkDismissed)
                MyIntent.DismissLinkedToOtherUserNotice -> dispatchEvent(MyEvent.LinkedToOtherUserDismissed)
            }
        }

        override fun reduce(
            state: MyUiState,
            event: MyEvent,
        ): MyUiState =
            when (event) {
                is MyEvent.ProfileChanged -> state.copy(profile = event.profile)
                is MyEvent.ThemeModeChanged -> state.copy(themeMode = event.mode)
                MyEvent.LogOutStarted -> state.copy(isLoggingOut = true)
                is MyEvent.AccountLinkRequested -> state.copy(accountLinkTarget = event.target)
                MyEvent.AccountLinkDismissed -> state.copy(accountLinkTarget = null)
                MyEvent.AccountLinkStarted -> state.copy(isLinkingAccount = true)
                MyEvent.AccountLinkFinished -> state.copy(isLinkingAccount = false, accountLinkTarget = null)
                MyEvent.LinkedToOtherUserNoticed -> state.copy(showsLinkedToOtherUserNotice = true)
                MyEvent.LinkedToOtherUserDismissed -> state.copy(showsLinkedToOtherUserNotice = false)
            }

        /**
         * 화면이 다시 보일 때의 갱신.
         *
         * 잔액은 채팅·제작·이프 충전에서 바뀌므로 탭에 들어올 때마다 다시 읽어야 한다. ViewModel 은
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

        /**
         * 재인증과 대상 인증으로 제공자 화면을 두 번 열어 오래 걸린다. 인텐트 루프에서 기다리면 그동안
         * 탭 갱신·테마 변경 같은 다음 입력이 밀리므로 자식 작업으로 떼어 내고 중복은 진행 플래그로 막는다.
         */
        private suspend fun startAccountLink() {
            if (accountLinkJob?.isActive == true) return
            val snapshot = uiState.value
            val target = snapshot.accountLinkTarget ?: return
            // 재인증은 이미 연동된 제공자로 한다. 아직 프로필을 못 읽었으면 시작할 근거가 없다.
            val current = snapshot.profile?.linkedProviders?.firstOrNull() ?: return
            dispatchEvent(MyEvent.AccountLinkStarted)
            accountLinkJob =
                viewModelScope.launch {
                    when (val result = accountLinkRepository.link(current, target)) {
                        is DomainResult.Success -> {
                            dispatchEffect(MyEffect.AccountLinked(target))
                            // 연동 결과의 정본은 프로필이다. 칩을 낙관적으로 더하지 않고 다시 읽는다.
                            refreshProfile()
                        }

                        is DomainResult.Failure -> noticeLinkFailure(result.error)
                    }
                    dispatchEvent(MyEvent.AccountLinkFinished)
                }
        }

        private suspend fun noticeLinkFailure(error: DomainError) {
            when (error.linkConflictCode()) {
                CODE_LINKED_TO_OTHER_USER -> dispatchEvent(MyEvent.LinkedToOtherUserNoticed)
                CODE_PROVIDER_ALREADY_LINKED -> {
                    dispatchEffect(MyEffect.AccountAlreadyLinked)
                    // 서버 상태가 화면과 다르다는 뜻이므로 표시를 맞춘다.
                    refreshProfile()
                }
                // 사용자가 제공자 창을 스스로 닫았다. 실패 안내를 띄우지 않는다.
                else -> if (error != DomainError.ProviderCancelled) dispatchEffect(MyEffect.AccountLinkFailed)
            }
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

/** 409 두 종류는 안내가 다르다. 그 밖의 실패는 코드로 갈리지 않는다. */
private fun DomainError.linkConflictCode(): String? =
    (this as? DomainError.Server)?.takeIf { it.status == HTTP_CONFLICT }?.code

private const val CODE_LINKED_TO_OTHER_USER = "SOCIAL_ACCOUNT_LINKED_TO_OTHER_USER"
private const val CODE_PROVIDER_ALREADY_LINKED = "PROVIDER_ALREADY_LINKED"
private const val HTTP_CONFLICT = 409
