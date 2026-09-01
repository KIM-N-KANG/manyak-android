package app.manyak.core.domain.credit

/**
 * 서버가 정본인 이프 적립·소모 수치.
 *
 * 필드가 null 이면 서버가 그 수치를 내려주지 않은 것이다. 대신 쓸 기본값을 앱에 두지 않는다 —
 * 앱이 들고 있는 값은 서버가 정책을 바꾸는 순간 거짓이 되고, 스토어 배포 주기가 길어 고치는 데도 오래 걸린다.
 */
data class CreditPolicy(
    /** 가입 보상 이프. 앱은 문구로 노출하지 않는다. */
    val signupReward: Long? = null,
    /** 초대 보상 이프. 초대자와 제출자가 각각 받는다. */
    val inviteReward: Long? = null,
    /** 초대자 몫 보상의 계정별 KST 월 한도. 이프가 아니라 횟수다. */
    val inviteMonthlyCap: Long? = null,
    /** 출석체크 보상 이프. KST 자정 기준 1일 1회다. */
    val attendanceReward: Long? = null,
    /** 간편 제작 스토리 완성에 드는 이프. */
    val storyCreationCost: Long? = null,
    /** 채팅 턴에 드는 이프. 재생성도 같은 값이다. */
    val chatTurnCost: Long? = null,
)
