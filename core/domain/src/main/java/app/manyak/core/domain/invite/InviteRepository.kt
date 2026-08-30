package app.manyak.core.domain.invite

import app.manyak.core.domain.error.DomainResult

/** 초대 코드 조회·등록. 보상 지급 결과는 잔액에 반영되므로 호출부가 프로필 갱신으로 잇는다. */
interface InviteRepository {
    /** 내 초대 코드와 이번 달 보상 진행. */
    suspend fun getMyInvite(): DomainResult<Invite>

    /**
     * 받은 초대 코드를 등록한다. 계정당 한 번만 성공하며 재시도는 409 로 돌아온다.
     *
     * 실패 사유 구분은 [app.manyak.core.domain.error.DomainError.Server] 의 상태·코드가 싣는다 —
     * 문구를 고르는 것은 화면의 몫이다.
     */
    suspend fun redeemInviteCode(code: String): DomainResult<Unit>
}
