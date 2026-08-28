package app.manyak.core.data.api.dto

import app.manyak.core.domain.story.StoryDetail
import app.manyak.core.domain.story.StoryStartSetting
import kotlinx.serialization.Serializable

/**
 * 상세 응답. 상세 화면이 그리지 않는 등록 상태·공개 범위·로어북·주요 사건·해시태그·좋아요 수는
 * 역직렬화하지 않는다.
 *
 * 식별자 밖의 필드에 기본값을 두는 이유는 목록 DTO 와 같다 — 서버가 필드를 하나 빼도 화면 전체가
 * 실패로 떨어지지 않게 한다.
 */
@Serializable
data class StoryDetailResponseDto(
    val id: String,
    val title: String = "",
    val oneLineIntro: String = "",
    val description: String? = null,
    val genres: List<String> = emptyList(),
    /** 히어로용 원본. 목록·카드가 쓰는 축소본(`thumbnailUrlSm`)과 다른 URL 이다. */
    val thumbnailUrl: String? = null,
    val turnCount: Long = 0,
    val createdAt: String? = null,
    val startSettings: List<StoryStartSettingDto> = emptyList(),
    val reachedEndings: List<String> = emptyList(),
)

/** 프롤로그·추천 입력은 상세가 그리지 않아 역직렬화하지 않는다. */
@Serializable
data class StoryStartSettingDto(
    val id: String,
    val name: String = "",
    val startSituation: String = "",
    val endings: List<StoryEndingDto> = emptyList(),
)

/**
 * 엔딩 하나. 엔딩은 유형 없이 이름으로 식별한다.
 *
 * 달성 조건(`requirement`)과 에필로그는 결말에 닿는 방법과 결말 자체를 담고 있어 받지 않는다.
 */
@Serializable
data class StoryEndingDto(
    val name: String = "",
)

fun StoryDetailResponseDto.toDomain(): StoryDetail =
    StoryDetail(
        id = id,
        title = title,
        oneLineIntro = oneLineIntro,
        description = description?.takeIf { text -> text.isNotBlank() },
        genres = genres.filter { genre -> genre.isNotBlank() },
        thumbnailUrl = thumbnailUrl?.takeIf { url -> url.isNotBlank() },
        turnCount = turnCount,
        createdDate = createdAt?.toDisplayDate(),
        startSettings =
            startSettings.map { setting ->
                StoryStartSetting(
                    id = setting.id,
                    name = setting.name,
                    startSituation = setting.startSituation,
                    endings = setting.endings.map { ending -> ending.name }.filter { name -> name.isNotBlank() },
                )
            },
        reachedEndings = reachedEndings.filter { ending -> ending.isNotBlank() },
    )
