package app.manyak.feature.login

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.session.SessionRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.common.entity.session.SessionState
import app.manyak.common.presentation.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginIntent {
    data class SignIn(
        val provider: AuthProvider,
    ) : LoginIntent

    data object DismissError : LoginIntent

    data object AcknowledgeNotice : LoginIntent
}

data class LoginUiState(
    /**
     * 진행 중인 제공자. null 이 아니면 두 버튼을 모두 비활성화한다.
     *
     * 화면이 아니라 세션 계층이 정본이다 — 외부 제공자 화면에 다녀오는 동안 프로세스가 재생성되면
     * 기다리던 continuation 이 사라지고, 진행 표시만 화면에 남으면 영구 로딩이 된다.
     */
    val inProgress: AuthProvider? = null,
    val error: DomainError? = null,
    /** 직전 세션이 끝난 이유. 사용자가 확인할 때까지 유지된다. */
    val notice: SessionEndNotice? = null,
)

sealed interface LoginEvent {
    /** 세션 계층이 알려 준 진행 상태. 프로세스가 재생성되면 null 로 돌아온다. */
    data class ProgressChanged(
        val provider: AuthProvider?,
    ) : LoginEvent

    data object Succeeded : LoginEvent

    data class Failed(
        val error: DomainError,
    ) : LoginEvent

    data object ErrorDismissed : LoginEvent

    data class NoticeChanged(
        val notice: SessionEndNotice?,
    ) : LoginEvent
}

/**
 * 로그인 화면.
 *
 * 두 제공자는 **같은 시작 함수를 provider 인자만 달리해** 호출하고, 성공 이후 처리는 provider 와
 * 무관하게 동일하다. 성공하면 세션 상태가 회원으로 바뀌고 루트가 그래프를 교체하므로
 * 이 화면은 이동 효과를 발행하지 않는다.
 */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val analytics: Analytics,
    ) : MviViewModel<LoginIntent, LoginUiState, LoginEvent, Nothing>(LoginUiState()) {
        init {
            analytics.track(AnalyticsEvent.LoginViewed)
            viewModelScope.launch {
                sessionRepository.sessionState
                    .filterIsInstance<SessionState.SignedOut>()
                    .map { it.notice }
                    .collect { notice -> dispatchEvent(LoginEvent.NoticeChanged(notice)) }
            }
            viewModelScope.launch {
                sessionRepository.signInInProgress.collect { provider ->
                    dispatchEvent(LoginEvent.ProgressChanged(provider))
                }
            }
        }

        override suspend fun handleIntent(intent: LoginIntent) {
            when (intent) {
                is LoginIntent.SignIn -> startSignIn(intent.provider)
                LoginIntent.DismissError -> dispatchEvent(LoginEvent.ErrorDismissed)
                LoginIntent.AcknowledgeNotice -> sessionRepository.acknowledgeSessionEndNotice()
            }
        }

        override fun reduce(
            state: LoginUiState,
            event: LoginEvent,
        ): LoginUiState =
            when (event) {
                is LoginEvent.ProgressChanged -> state.copy(inProgress = event.provider)
                LoginEvent.Succeeded -> state.copy(error = null)
                is LoginEvent.Failed -> state.copy(error = event.error)
                LoginEvent.ErrorDismissed -> state.copy(error = null)
                is LoginEvent.NoticeChanged -> state.copy(notice = event.notice)
            }

        /**
         * 진행 중에는 새 로그인을 시작하지 않는다. 화면의 잠금과 별개로 여기서도 막아,
         * 빠른 연속 탭이 제공자 창을 두 번 열지 않게 한다.
         */
        private suspend fun startSignIn(provider: AuthProvider) {
            if (sessionRepository.signInInProgress.value != null) return
            analytics.track(AnalyticsEvent.LoginProviderButtonClicked(provider))
            dispatchEvent(LoginEvent.ErrorDismissed)
            when (val result = sessionRepository.signIn(provider)) {
                is DomainResult.Success -> dispatchEvent(LoginEvent.Succeeded)
                is DomainResult.Failure -> {
                    // 사용자가 제공자 창을 스스로 닫은 것은 실패 안내가 뜨지 않으므로 이벤트도 없다.
                    if (result.error != DomainError.ProviderCancelled) {
                        analytics.track(
                            AnalyticsEvent.LoginOauthErrorShown(result.error::class.simpleName.orEmpty(), provider),
                        )
                    }
                    dispatchEvent(LoginEvent.Failed(result.error))
                }
            }
        }
    }
