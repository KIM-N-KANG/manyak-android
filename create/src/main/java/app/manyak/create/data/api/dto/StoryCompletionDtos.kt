package app.manyak.create.data.api.dto

import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.StoryCompletionCommand
import kotlinx.serialization.Serializable

@Serializable
data class StoryCompletionRequestDto(
    val requestId: String,
    val simpleCreationId: Long,
    val storylineId: Long,
    val additionalInfos: List<String>,
)

fun StoryCompletionCommand.toRequestDto(): StoryCompletionRequestDto =
    StoryCompletionRequestDto(
        requestId = requestId,
        simpleCreationId = simpleCreationId,
        storylineId = storylineId,
        additionalInfos = additionalInfos,
    )

/** 응답의 시작 설정·엔딩 등은 채팅 진입 연동 전까지 쓰지 않아 역직렬화하지 않는다. */
@Serializable
data class StoryCompletionResponseDto(
    val id: String,
    val title: String = "",
)

fun StoryCompletionResponseDto.toDomain(): CompletedStory = CompletedStory(id = id, title = title)
