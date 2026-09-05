package app.manyak.core.data.api.dto

import app.manyak.common.entity.story.CreationRequestSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/** `result` 는 단계별로 원 POST 응답과 같은 스키마라, 단계를 읽은 뒤에야 해석할 수 있다. */
@Serializable
data class CreationRequestStatusDto(
    val stage: String,
    val status: String,
    val result: JsonElement? = null,
)

/**
 * 알 수 없는 단계·상태, `COMPLETED` 인데 결과가 없는 응답은 계약 불일치이므로 null 을 돌려주고
 * 호출부가 직렬화 오류로 취급한다. 결과 해석이 던지는 `SerializationException` 도 호출부가 잡는다.
 */
fun CreationRequestStatusDto.toDomainOrNull(json: Json): CreationRequestSnapshot? =
    when (status) {
        "PENDING" -> CreationRequestSnapshot.Pending
        "FAILED" -> CreationRequestSnapshot.Failed
        "COMPLETED" -> {
            val element = result
            when {
                element == null -> null

                stage == "STORYLINE_GENERATION" ->
                    CreationRequestSnapshot.StorylinesReady(
                        json.decodeFromJsonElement<StorylineGenerationResponseDto>(element).toDomain(),
                    )

                stage == "STORY_COMPLETION" ->
                    CreationRequestSnapshot.StoryReady(
                        json.decodeFromJsonElement<StoryCompletionResponseDto>(element).toDomain(),
                    )

                else -> null
            }
        }

        else -> null
    }
