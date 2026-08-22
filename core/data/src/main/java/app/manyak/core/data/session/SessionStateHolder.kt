package app.manyak.core.data.session

import app.manyak.core.domain.session.SessionEndNotice
import app.manyak.core.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 공개 세션 상태와 **세션 세대**를 소유한다.
 *
 * 세대가 필요한 이유는 취소만으로 부족하기 때문이다 — 취소되지 않는 네트워크 호출이 정리 뒤에 늦게
 * 돌아와 옛 세션을 되살릴 수 있다. 그래서 인증 응답·프로필 저장·재발급 커밋은 시작할 때 받은 세대가
 * 아직 현재 세대인지 **커밋 직전에** 확인하고, 다르면 결과를 버린다.
 */
@Singleton
class SessionStateHolder
    @Inject
    constructor() {
        private val state = MutableStateFlow<SessionState>(SessionState.Undetermined)
        private val generation = AtomicLong(0)

        val sessionState: StateFlow<SessionState> = state.asStateFlow()

        val currentGeneration: Long get() = generation.get()

        fun isCurrentGeneration(observed: Long): Boolean = observed == generation.get()

        /**
         * 종료를 시작한다. 세대를 올려 진행 중이던 작업의 커밋을 무효화하고, 정리가 끝날 때까지
         * 공개 상태를 [SessionState.Undetermined] 로 되돌린다 — 정리 전에 인증 그래프를 열면
         * 새 로그인이 이전 사용자의 잔여 상태 위에서 시작된다.
         */
        fun beginTermination(): Long {
            state.value = SessionState.Undetermined
            return generation.incrementAndGet()
        }

        fun publishMember() {
            state.value = SessionState.Member
        }

        fun publishSignedOut(notice: SessionEndNotice?) {
            state.value = SessionState.SignedOut(notice)
        }

        /** 사용자가 종료 안내를 확인했다. 상태는 미로그인 그대로 두고 안내만 지운다. */
        fun clearNotice() {
            state.update { current -> if (current is SessionState.SignedOut) SessionState.SignedOut(null) else current }
        }
    }
