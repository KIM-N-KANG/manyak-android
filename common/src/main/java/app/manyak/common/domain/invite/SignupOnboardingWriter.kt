package app.manyak.common.domain.invite

/** 신규 가입 성공 후 회원 화면에서 보여 줄 안내를 기록한다. */
interface SignupOnboardingWriter {
    suspend fun markPending()
}
