package app.manyak.common.domain.invite

/**
 * 신규 가입 성공 후 회원 화면에서 보여 줄 안내를 기록한다.
 *
 * 안내마다 소유 기능이 자기 기록기를 `@IntoSet` 으로 등록하고, 인증은 가입을 확인하면 모두 부른다.
 */
interface SignupOnboardingWriter {
    suspend fun markPending()
}
