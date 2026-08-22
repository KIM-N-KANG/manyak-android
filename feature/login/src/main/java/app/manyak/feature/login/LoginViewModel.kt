package app.manyak.feature.login

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.session.SessionEndNotice
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.session.SessionState
import app.manyak.core.ui.mvi.MviViewModel
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
    /** 진행 중인 제공자. null 이 아니면 두 버튼을 모두 비활성화한다. */
    val inProgress: AuthProvider? = null,
    val error: DomainError? = null,
    /** 직전 세션이 끝난 이유. 사용자가 확인할 때까지 유지된다. */
    val notice: SessionEndNotice? = null,
)

sealed interface LoginEvent {
    data class Started(
        val provider: AuthProvider,
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
 * 무관하게 동일하다(하네스 §3-3-4). 성공하면 세션 상태가 회원으로 바뀌고 루트가 그래프를 교체하므로
 * 이 화면은 이동 효과를 발행하지 않는다.
 */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
    ) : MviViewModel<LoginIntent, LoginUiState, LoginEvent, Nothing>(LoginUiState()) {
        init {
            viewModelScope.launch {
                sessionRepository.sessionState
                    .filterIsInstance<SessionState.SignedOut>()
                    .map { it.notice }
                    .collect { notice -> dispatchEvent(LoginEvent.NoticeChanged(notice)) }
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
                is LoginEvent.Started -> state.copy(inProgress = event.provider, error = null)
                LoginEvent.Succeeded -> state.copy(inProgress = null, error = null)
                is LoginEvent.Failed -> state.copy(inProgress = null, error = event.error)
                LoginEvent.ErrorDismissed -> state.copy(error = null)
                is LoginEvent.NoticeChanged -> state.copy(notice = event.notice)
            }

        /**
         * 진행 중에는 새 로그인을 시작하지 않는다. 화면의 잠금과 별개로 여기서도 막아,
         * 빠른 연속 탭이 제공자 창을 두 번 열지 않게 한다.
         */
        private suspend fun startSignIn(provider: AuthProvider) {
            if (uiState.value.inProgress != null) return
            dispatchEvent(LoginEvent.Started(provider))
            when (val result = sessionRepository.signIn(provider)) {
                is DomainResult.Success -> dispatchEvent(LoginEvent.Succeeded)
                is DomainResult.Failure -> dispatchEvent(LoginEvent.Failed(result.error))
            }
        }
    }
