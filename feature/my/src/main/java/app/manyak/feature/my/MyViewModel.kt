package app.manyak.feature.my

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MyIntent {
    data object LogOut : MyIntent

    data object Refresh : MyIntent
}

data class MyUiState(
    val profile: UserProfile? = null,
    val isLoggingOut: Boolean = false,
)

sealed interface MyEvent {
    data class ProfileChanged(
        val profile: UserProfile?,
    ) : MyEvent

    data object LogOutStarted : MyEvent
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
    ) : MviViewModel<MyIntent, MyUiState, MyEvent, Nothing>(MyUiState()) {
        init {
            viewModelScope.launch {
                userProfileRepository.profile.collect { profile -> dispatchEvent(MyEvent.ProfileChanged(profile)) }
            }
        }

        override suspend fun handleIntent(intent: MyIntent) {
            when (intent) {
                MyIntent.LogOut -> logOut()
                // 실패는 캐시된 값을 그대로 두는 것으로 흡수한다. 세션 상태를 바꾸지 않는다.
                MyIntent.Refresh -> userProfileRepository.refresh()
            }
        }

        override fun reduce(
            state: MyUiState,
            event: MyEvent,
        ): MyUiState =
            when (event) {
                is MyEvent.ProfileChanged -> state.copy(profile = event.profile)
                MyEvent.LogOutStarted -> state.copy(isLoggingOut = true)
            }

        private suspend fun logOut() {
            if (uiState.value.isLoggingOut) return
            dispatchEvent(MyEvent.LogOutStarted)
            sessionRepository.signOut()
        }
    }
