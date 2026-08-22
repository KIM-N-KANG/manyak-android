package app.manyak.root

import androidx.lifecycle.ViewModel
import app.manyak.core.domain.session.SessionRepository
import app.manyak.core.domain.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** 루트는 세션 상태만 본다. 그래프 전환은 이동 효과가 아니라 이 상태의 결과다(하네스 §3-3-3). */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        sessionRepository: SessionRepository,
    ) : ViewModel() {
        val sessionState: StateFlow<SessionState> = sessionRepository.sessionState
    }
