package app.manyak.common.entity.auth

/**
 * 소셜 로그인 제공자.
 *
 * [wireName]은 서버 경로(`POST /auth/login/{provider}`)와 프로필의 연동 목록에 쓰이는 값이며
 * 소문자로 고정한다(서버 계약).
 */
enum class AuthProvider(
    val wireName: String,
) {
    GOOGLE("google"),
    KAKAO("kakao"),
    ;

    companion object {
        fun fromWireName(value: String): AuthProvider? = entries.firstOrNull { it.wireName == value.lowercase() }
    }
}
