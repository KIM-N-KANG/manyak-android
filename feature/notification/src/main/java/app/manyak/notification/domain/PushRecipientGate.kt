package app.manyak.notification.domain

import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.domain.user.UserProfileRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 현재 회원이 이 메시지의 수신자인지 판정한다.
 *
 * 토큰 삭제 성공이나 `회원` 여부만으로는 판정하지 않는다 — 로그아웃 뒤 남은 토큰이나 이전 회원 대상으로
 * 이미 발송된 메시지가 다음 로그인 회원 기기에 도착할 수 있다. 서버가 실은 수신자 ID 를 프로필 캐시와
 * 비교하고, 없으면 버린다.
 */
class PushRecipientGate
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val profileRepository: UserProfileRepository,
    ) {
        /**
         * 세션이 확정되고 프로필이 도착할 때까지 기다린다 — 콜드 스타트·로그인 직후를 덮는다.
         * 호출부가 대기 상한을 건다.
         */
        suspend fun admits(recipientId: String?): Boolean {
            if (recipientId == null) return false
            val session = sessionRepository.sessionState.first { it != SessionState.Undetermined }
            if (session != SessionState.Member) return false
            val profile = profileRepository.profile.filterNotNull().first()
            // 프로필을 기다리는 사이 로그아웃이 시작됐을 수 있다.
            return profile.id == recipientId && isMemberNow()
        }

        /** 표시 직전에 한 번 더 본다. 이 뒤의 밀리초 창은 종료 정리의 알림 전체 취소가 덮는다. */
        fun isMemberNow(): Boolean = sessionRepository.sessionState.value == SessionState.Member
    }
