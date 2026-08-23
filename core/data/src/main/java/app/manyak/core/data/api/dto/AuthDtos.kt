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

@Serializable
data class ApiErrorResponseDto(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)
