package app.manyak.auth.entity

import app.manyak.common.entity.consent.RequiredConsent

/** 로그인 성공 결과. */
sealed interface SignInOutcome {
    /** 세션이 열렸다. [isNewUser]가 참이면 신규 가입 초대 코드 안내 대상이다. */
    data class Completed(
        val isNewUser: Boolean,
    ) : SignInOutcome

    /** 계정과 세션 없이 필수 동의를 기다린다. 시트는 [PendingSignup]을 보고 띄운다. */
    data object ConsentRequired : SignInOutcome
}

/** 필수 동의를 마쳐야 완료되는 소셜 가입. 대기 코드는 저장소 안에만 두고 이 모델에 싣지 않는다. */
data class PendingSignup(
    val required: List<RequiredConsent>,
)
