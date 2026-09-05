package app.manyak.core.data.api.dto

import app.manyak.common.data.time.toDisplayDate
import app.manyak.common.entity.story.StorySummary
import kotlinx.serialization.Serializable

/**
 * 목록 응답 한 건. 오리지널·내 스토리 목록이 같은 모양을 쓴다. 카드가 쓰지 않는
 * 좋아요 수·등록 상태는 역직렬화하지 않는다.
 *
 * 식별자 밖의 필드에 기본값을 두는 이유는 서버가 필드를 하나 빼도 목록 전체가 실패로
 * 떨어지지 않게 하기 위해서다.
 */
@Serializable
data class StorySummaryDto(
    val id: String,
    val title: String = "",
    val thumbnailUrlSm: String? = null,
    val author: StoryAuthorDto? = null,
    val oneLineIntro: String = "",
    val genres: List<String> = emptyList(),
    val turnCount: Long = 0,
    val createdAt: String? = null,
)

@Serializable
data class StoryAuthorDto(
    val nickname: String? = null,
)

fun StorySummaryDto.toDomain(): StorySummary =
    StorySummary(
        id = id,
        title = title,
        authorNickname = author?.nickname?.takeIf { nickname -> nickname.isNotBlank() },
        thumbnailUrl = thumbnailUrlSm?.takeIf { url -> url.isNotBlank() },
        oneLineIntro = oneLineIntro,
        genres = genres.filter { genre -> genre.isNotBlank() },
        turnCount = turnCount,
        createdDate = createdAt?.toDisplayDate(),
    )
