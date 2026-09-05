package app.manyak.common.domain.invite

import kotlinx.coroutines.flow.Flow

/**
 * 신규 가입자에게 초대 코드 안내를 아직 보여 주지 않았다는 표시.
 *
 * 로그인 응답의 `isNewUser` 는 그 순간에만 존재하는데, 로그인 성공과 동시에 인증 백스택이 통째로
 * 사라져 로그인 화면에서는 안내를 띄울 수 없다. 그래서 **앱 스코프의 지속 상태로 두고 사용자가
 * 확인할 때까지 유지한다** — 안내를 보기 전에 앱이 죽어도 다음 실행에서 다시 뜬다.
 *
 * 건너뛰어도 자격(계정당 1회)은 서버가 들고 있으므로, 이 표시는 "안내를 보여 줄 차례인가"만 뜻한다.
 */
interface InviteOnboardingRepository {
    val pending: Flow<Boolean>

    /** 신규 가입으로 로그인했을 때 세운다. */
    suspend fun markPending()

    /** 사용자가 등록했거나 건너뛰었다. 실패해도 화면은 닫고, 다음 실행에서 다시 뜬다. */
    suspend fun acknowledge()
}
