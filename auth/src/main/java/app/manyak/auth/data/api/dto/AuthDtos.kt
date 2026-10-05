package app.manyak.auth.data.api.dto

import app.manyak.common.entity.consent.ConsentItem
import app.manyak.common.entity.consent.RequiredConsent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 와이어 DTO 는 이 모듈 밖으로 나가지 않는다. 도메인 모델로 바꾸는 것도 여기서 한다.
 */
@Serializable
data class SocialLoginRequestDto(
    val idToken: String,
)

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
    @SerialName("isNewUser")
    val isNewUser: Boolean = false,
)

/** `expiresAt`·`isNewUser` 는 받되 쓰지 않는다. 만료 판정은 서버가 하고, 가입 여부는 완료 응답이 준다. */
@Serializable
data class SocialAuthResponseDto(
    val status: String? = null,
    val token: TokenResponseDto? = null,
    val consentToken: String? = null,
    val consents: SignupConsentsDto? = null,
)

@Serializable
data class ConsentStatusDto(
    val requiredVersion: String? = null,
    val needsConsent: Boolean? = null,
)

@Serializable
data class SignupConsentsDto(
    val terms: ConsentStatusDto? = null,
    val privacy: ConsentStatusDto? = null,
    val age14: ConsentStatusDto? = null,
)

/** 수락한 버전만 싣는다. null 필드는 직렬화에서 빠지고 서버는 미제출로 본다. */
@Serializable
data class SignupConsentRequestDto(
    val terms: String? = null,
    val privacy: String? = null,
    val age14: String? = null,
)

/** 누락되거나 해석할 수 없는 항목이 있으면 null 이다. 그런 응답으로 동의를 받지 않는다. */
fun SignupConsentsDto.requiredOrNull(): List<RequiredConsent>? {
    val statuses = listOf(ConsentItem.AGE14 to age14, ConsentItem.TERMS to terms, ConsentItem.PRIVACY to privacy)
    if (statuses.any { (_, status) -> status?.needsConsent == null || status.requiredVersion.isNullOrBlank() }) {
        return null
    }
    return statuses.mapNotNull { (item, status) ->
        if (status?.needsConsent == true) RequiredConsent(item, status.requiredVersion.orEmpty()) else null
    }
}

/** 재인증은 로그인과 달리 어느 제공자로 증명하는지를 본문에 함께 싣는다. */
@Serializable
data class LinkReauthRequestDto(
    val provider: String,
    val idToken: String,
)

/** `expiresAt` 은 받되 쓰지 않는다 — 코드는 곧바로 다음 호출에 소비되고 만료 판정은 서버가 한다. */
@Serializable
data class LinkReauthResponseDto(
    val linkCode: String,
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String,
)
