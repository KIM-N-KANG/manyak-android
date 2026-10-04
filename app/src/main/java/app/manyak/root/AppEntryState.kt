package app.manyak.root

import app.manyak.auth.entity.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.runningFold

/** 로그인 화면에 진입한 뒤의 인증과, 저장된 세션으로 시작한 실행을 구분한다. */
internal data class AppEntryState(
    val session: SessionState,
    val isStartup: Boolean = session !is SessionState.SignedOut,
) {
    fun onSessionChanged(session: SessionState): AppEntryState =
        AppEntryState(session, isStartup && session !is SessionState.SignedOut)
}

internal fun StateFlow<SessionState>.appEntryStates(): Flow<AppEntryState> =
    runningFold(AppEntryState(value)) { entry, session -> entry.onSessionChanged(session) }
