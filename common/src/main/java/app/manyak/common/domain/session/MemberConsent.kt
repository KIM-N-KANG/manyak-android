package app.manyak.common.domain.session

import kotlinx.coroutines.flow.StateFlow

/** 현재 회원의 필수 동의를 서버에서 확인한 결과. 프로세스 시작과 계정 변경 때는 다시 확인한다. */
interface MemberConsent {
    val isSatisfied: StateFlow<Boolean>
}
