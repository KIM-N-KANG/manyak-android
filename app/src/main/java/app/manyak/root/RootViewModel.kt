package app.manyak.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.session.SessionState
import app.manyak.session.SessionTerminationCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 루트는 세션 상태만 본다. 그래프 전환은 이동 효과가 아니라 이 상태의 결과다. */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        private val coordinator: SessionTerminationCoordinator,
    ) : ViewModel() {
        val sessionState: StateFlow<SessionState> = sessionRepository.sessionState

        /**
         * 끝내지 못한 종료 정리를 다시 시도한다.
         *
         * 정리는 화면이 사라져도 끝나야 하므로 조정자가 앱 스코프에서 실행한다. 여기서는 시작만 시킨다.
         */
        fun onRetryCleanup() {
            viewModelScope.launch { coordinator.retryCleanup() }
        }
    }
