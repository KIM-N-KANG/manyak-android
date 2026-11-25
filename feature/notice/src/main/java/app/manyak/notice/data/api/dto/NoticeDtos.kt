package app.manyak.notice.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NoticePageDto(
    val items: List<NoticeItemDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class NoticeItemDto(
    val noticeId: String,
    val title: String,
    val tag: String? = null,
    val publishedAt: String? = null,
)

@Serializable
data class NoticeDetailDto(
    val noticeId: String,
    val title: String,
    val tag: String? = null,
    val body: String = "",
    val publishedAt: String? = null,
    val updatedAt: String? = null,
)
