package app.manyak.core.data.session

import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.common.entity.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 공개 세션 상태를 소유한다. 세대와 종료 장벽은 [SessionGate] 가 소유한다 — 상태 발행과
 * 커밋 허용 판정이 한 클래스에 섞이면 "발행했으니 유효하다"는 오해가 생긴다.
 *
 * 상태를 바꾸는 쪽은 언제나 [SessionGate.commit] 이나 종료 절차를 거친다.
 */
@Singleton
class SessionStateHolder
    @Inject
    constructor() {
        private val state = MutableStateFlow<SessionState>(SessionState.Undetermined)

        val sessionState: StateFlow<SessionState> = state.asStateFlow()

        /**
         * 정리가 끝날 때까지 어느 그래프도 열지 않는다 — 정리 전에 인증 그래프를 열면
         * 새 로그인이 이전 사용자의 잔여 상태 위에서 시작된다.
         */
        fun publishUndetermined() {
            state.value = SessionState.Undetermined
        }

        fun publishMember() {
            state.value = SessionState.Member
        }

        fun publishSignedOut(notice: SessionEndNotice?) {
            state.value = SessionState.SignedOut(notice)
        }

        /** 재시도를 소진하고도 정리가 끝나지 않았다. 인증 그래프 대신 재시도 경로만 연다. */
        fun publishCleanupFailed(notice: SessionEndNotice) {
            state.value = SessionState.CleanupFailed(notice)
        }

        /** 사용자가 종료 안내를 확인했다. 상태는 미로그인 그대로 두고 안내만 지운다. */
        fun clearNotice() {
            state.update { current -> if (current is SessionState.SignedOut) SessionState.SignedOut(null) else current }
        }
    }
