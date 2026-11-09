package app.manyak.my.profile.data.dto

import kotlinx.serialization.Serializable

/** null 필드는 직렬화에서 빠진다. 서버는 보낸 필드만 바꾼다. */
@Serializable
data class UpdateProfileRequestDto(
    val nickname: String? = null,
    val profileImagePreset: String? = null,
)

@Serializable
data class ProfilePresetResponseDto(
    val key: String,
    val imageUrl: String,
    val thumbnailBase64: String? = null,
)
