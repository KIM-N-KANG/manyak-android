package app.manyak.my.profile.domain

/** 저장 전에 거르는 닉네임 규칙 위반. 서버 규칙과 같고, 상한은 입력 칸이 막는다. */
enum class NicknameViolation { TOO_SHORT, INVALID_CHARACTER }

const val NICKNAME_MIN_LENGTH = 2
const val NICKNAME_MAX_LENGTH = 20

/** 완성형 한글, 영문, 숫자만 허용한다. 공백과 자모 단독도 서버가 거부한다. */
private val AllowedNickname = Regex("^[가-힣A-Za-z0-9]*$")

/** 서버는 입력값을 다듬지 않고 그대로 센다. 허용 문자를 길이보다 먼저 본다. */
fun validateNickname(nickname: String): NicknameViolation? =
    when {
        !AllowedNickname.matches(nickname) -> NicknameViolation.INVALID_CHARACTER
        nickname.length < NICKNAME_MIN_LENGTH -> NicknameViolation.TOO_SHORT
        else -> null
    }
