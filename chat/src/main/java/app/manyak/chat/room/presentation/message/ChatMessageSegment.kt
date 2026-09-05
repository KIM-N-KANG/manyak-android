package app.manyak.chat.room.presentation.message

import app.manyak.designsystem.component.isAllowedCharacterImageUrl

/** AI 출력을 그리는 단위. 저장 본문과 스트리밍 조각이 같은 목록으로 환원돼 한 렌더러를 쓴다. */
sealed interface ChatMessageSegment {
    data class Text(
        val content: String,
    ) : ChatMessageSegment

    data class CharacterImage(
        val name: String,
        val imageUrl: String,
    ) : ChatMessageSegment
}

/**
 * 저장된 AI 본문의 인물 이미지 마커를 조각 목록으로 바꾼다.
 *
 * 마커로 인정하려면 **세 조건을 모두** 만족해야 한다.
 * 1. 그 줄 전체가 `[[https://...]]` 일 것
 * 2. 마커 줄 바로 뒤가 빈 줄일 것
 * 3. 빈 줄 다음 줄이 `이름:` 라벨로 시작할 것
 *
 * 하나라도 어긋나면 **그 줄을 평문으로 그대로 남긴다.** 조용히 지우면 서버가 보낸 본문이 화면에서
 * 사라진 것을 아무도 알 수 없다.
 */
fun parseChatMessageSegments(content: String): List<ChatMessageSegment> {
    val normalized = content.replace("\r\n", "\n")
    val markers = findMarkers(normalized)
    if (markers.isEmpty()) {
        return if (content.isEmpty()) emptyList() else listOf(ChatMessageSegment.Text(content))
    }

    val segments = mutableListOf<ChatMessageSegment>()
    var cursor = 0
    for (marker in markers) {
        // 마커 줄 앞의 줄바꿈은 이미지 블록의 경계다. 남기면 이미지 위 간격이 두 번 들어간다.
        val before = normalized.substring(cursor, marker.start).removeSuffix("\n")
        if (before.isNotEmpty()) segments += ChatMessageSegment.Text(before)
        segments += ChatMessageSegment.CharacterImage(name = marker.name, imageUrl = marker.imageUrl)
        cursor = marker.end
    }
    val remaining = normalized.substring(cursor)
    if (remaining.isNotEmpty()) segments += ChatMessageSegment.Text(remaining)
    return segments
}

/**
 * 스트리밍 토큰을 마지막 텍스트 조각에 이어 붙인다.
 *
 * 조각을 새로 만들지 않고 이어 붙이는 이유는 한 문단이 조각 수십 개로 쪼개지면 강조 마커가
 * 조각 경계에서 끊겨 파싱되지 않기 때문이다.
 */
fun List<ChatMessageSegment>.appendText(content: String): List<ChatMessageSegment> {
    if (content.isEmpty()) return this
    val last = lastOrNull()
    return if (last is ChatMessageSegment.Text) {
        dropLast(1) + ChatMessageSegment.Text(last.content + content)
    } else {
        this + ChatMessageSegment.Text(content)
    }
}

/**
 * 스트리밍 중 도착한 인물 이미지를 지금 위치에 끼운다.
 *
 * **직전 텍스트가 줄바꿈으로 끝나면 그 줄바꿈 하나를 지운다** — 이미지 블록의 경계이지 본문의 빈
 * 줄이 아니라서, 남겨 두면 이미지 위 간격이 두 번 들어간다. 이름이 비었거나 허용하지 않는 URL 이면
 * 아무것도 하지 않는다.
 */
fun List<ChatMessageSegment>.appendCharacterImage(
    name: String,
    imageUrl: String,
): List<ChatMessageSegment> {
    if (name.isBlank() || !isAllowedCharacterImageUrl(imageUrl)) return this

    val last = lastOrNull()
    val head =
        if (last is ChatMessageSegment.Text && last.content.endsWith("\n")) {
            val trimmed = last.content.dropLast(1)
            if (trimmed.isEmpty()) dropLast(1) else dropLast(1) + ChatMessageSegment.Text(trimmed)
        } else {
            this
        }
    return head + ChatMessageSegment.CharacterImage(name = name.trim(), imageUrl = imageUrl)
}

private data class MarkerMatch(
    val start: Int,
    val end: Int,
    val name: String,
    val imageUrl: String,
)

private fun findMarkers(content: String): List<MarkerMatch> {
    val matches = mutableListOf<MarkerMatch>()
    var lineStart = 0

    while (lineStart <= content.length) {
        val lineBreak = content.indexOf('\n', lineStart)
        val lineEnd = if (lineBreak == -1) content.length else lineBreak
        val imageUrl = MarkerLine.matchEntire(content.substring(lineStart, lineEnd))?.groupValues?.get(1)

        if (imageUrl != null && content.startsWith("\n\n", lineEnd) && isAllowedCharacterImageUrl(imageUrl)) {
            val speakerLineStart = lineEnd + BLANK_LINE_LENGTH
            val name = speakerName(content, speakerLineStart)
            if (name != null) {
                matches += MarkerMatch(start = lineStart, end = speakerLineStart, name = name, imageUrl = imageUrl)
            }
        }

        if (lineBreak == -1) break
        lineStart = lineBreak + 1
    }
    return matches
}

/** 마커 뒤 대사 줄의 `이름:` 라벨에서 인물 이름을 뽑는다. 라벨이 없으면 마커가 아니다. */
private fun speakerName(
    content: String,
    speakerLineStart: Int,
): String? {
    if (speakerLineStart > content.length) return null
    val lineBreak = content.indexOf('\n', speakerLineStart)
    val line = content.substring(speakerLineStart, if (lineBreak == -1) content.length else lineBreak)
    val name =
        SpeakerLabel
            .find(line.trimStart(' ', '\t'))
            ?.groupValues
            ?.get(1)
            ?.trim()
    return name?.takeIf { it.isNotEmpty() }
}

private val MarkerLine = Regex("""^\[\[(https://[^\r\n]+)]]$""")

private val SpeakerLabel = Regex("""^(.+?)[ \t]*:(?=[ \t]|$)""")

/** 마커 줄과 대사 줄 사이의 빈 줄(`\n\n`) 길이. */
private const val BLANK_LINE_LENGTH = 2
