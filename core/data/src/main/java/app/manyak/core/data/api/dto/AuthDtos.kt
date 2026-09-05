package app.manyak.core.data.api.dto

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

@Serializable
data class MeResponseDto(
    val id: String,
    val nickname: String,
    val profileImageUrl: String? = null,
    val profileThumbnailBase64: String? = null,
    val status: String,
    val creditBalance: Long = 0,
    val attendedToday: Boolean = false,
    val linkedProviders: List<String> = emptyList(),
)
