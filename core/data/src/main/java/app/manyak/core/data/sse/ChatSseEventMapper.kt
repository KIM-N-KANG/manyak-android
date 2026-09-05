package app.manyak.core.data.sse

import app.manyak.common.domain.error.DomainError
import app.manyak.common.entity.chat.ChatStreamEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * SSE 사건 이름과 `data` 원문을 화면이 아는 사건으로 옮긴다.
 *
 * 프레이밍(`event:`·`data:` 줄 묶기)은 okhttp-sse 가 이미 처리하므로 **앱이 소유하는 규칙은 이 함수
 * 하나다.** 규칙은 웹 클라이언트와 같다 — 같은 스트림이 두 클라이언트에서 다르게 읽히면 안 된다.
 *
 * 해석할 수 없는 사건은 `null` 로 버린다. 알 수 없는 이름을 실패로 만들면 서버가 사건을 하나
 * 늘릴 때마다 앱이 멈춘다.
 */
internal fun chatStreamEventOf(
    type: String?,
    data: String,
): ChatStreamEvent? =
    when (type) {
        EVENT_STARTED -> ChatStreamEvent.Started
        EVENT_TOKEN -> data.tokenText()?.let(ChatStreamEvent::Token)
        EVENT_CHARACTER_IMAGE -> data.characterImage()
        EVENT_COMPLETED -> ChatStreamEvent.Completed
        EVENT_ERROR -> ChatStreamEvent.Failed(DomainError.Unknown, data.stringField(FIELD_MESSAGE))
        else -> null
    }

/**
 * 토큰 본문. **JSON 이 아니면 원문 전체를 본문으로 본다** — 서버가 `data: 안녕` 처럼 평문으로 보내는
 * 경우가 계약에 함께 들어 있다. JSON 인데 `text` 가 없으면 해석할 수 없는 사건이라 버린다.
 */
private fun String.tokenText(): String? {
    val trimmed = trim()
    val element =
        try {
            lenientJson.parseToJsonElement(trimmed)
        } catch (_: SerializationException) {
            return trimmed
        }
    // JSON 으로 읽혔는데 객체가 아니면 버린다. 웹과 같은 판정이며, 여기서만 갈리면 숫자로만 이루어진
    // 조각이 한쪽 클라이언트에서만 사라진다.
    return (element as? JsonObject)?.stringOrNull(FIELD_TEXT)
}

/** 이름과 URL 이 **둘 다** 있어야 이미지다. 하나만 온 사건으로는 그릴 수도 읽어 줄 수도 없다. */
private fun String.characterImage(): ChatStreamEvent.CharacterImage? {
    val name = stringField(FIELD_NAME)?.takeIf(String::isNotEmpty) ?: return null
    val imageUrl = stringField(FIELD_IMAGE_URL)?.takeIf(String::isNotEmpty) ?: return null
    return ChatStreamEvent.CharacterImage(name = name, imageUrl = imageUrl)
}

/** 토큰과 달리 JSON 이 아니면 값이 없는 것으로 본다. */
private fun String.stringField(field: String): String? = parseJsonOrNull(trim())?.stringOrNull(field)

private fun parseJsonOrNull(raw: String): JsonObject? =
    try {
        lenientJson.parseToJsonElement(raw) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

private fun JsonObject.stringOrNull(field: String): String? {
    val value = this[field] ?: return null
    if (value is JsonNull) return null
    return (value as? JsonPrimitive)?.content
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private const val EVENT_STARTED = "started"
private const val EVENT_TOKEN = "token"
private const val EVENT_CHARACTER_IMAGE = "character_image"
private const val EVENT_COMPLETED = "completed"
private const val EVENT_ERROR = "error"

private const val FIELD_TEXT = "text"
private const val FIELD_NAME = "name"
private const val FIELD_IMAGE_URL = "imageUrl"
private const val FIELD_MESSAGE = "message"
