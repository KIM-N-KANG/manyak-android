package app.manyak.auth.entity

/**
 * 로그인 성공 결과.
 *
 * [isNewUser]가 참이면 신규 가입 초대 코드 안내 대상이다.
 */
data class SignInOutcome(
    val isNewUser: Boolean,
)
