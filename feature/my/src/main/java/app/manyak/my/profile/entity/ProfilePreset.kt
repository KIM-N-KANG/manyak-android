package app.manyak.my.profile.entity

/** 고를 수 있는 프로필 이미지. [key] 를 수정 요청에 그대로 싣는다. */
data class ProfilePreset(
    val key: String,
    val imageUrl: String,
    val thumbnailBase64: String?,
)
