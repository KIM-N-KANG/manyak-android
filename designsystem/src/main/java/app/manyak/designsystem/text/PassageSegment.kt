package app.manyak.designsystem.text

import app.manyak.designsystem.component.isAllowedCharacterImageUrl
import app.manyak.designsystem.component.isSceneImageUrl

/**
 * 이미지 마커가 섞인 본문을 그리는 단위. 채팅 AI 출력·프롤로그와 스토리 상세의 상황 설명이 같은
 * 목록으로 환원돼 같은 규칙을 쓴다.
 */
sealed interface PassageSegment {
    data class Text(
        val content: String,
    ) : PassageSegment

    data class CharacterImage(
        val name: String,
        val imageUrl: String,
    ) : PassageSegment

    data class SceneImage(
        val imageUrl: String,
    ) : PassageSegment
}

/**
 * 저장된 본문의 이미지 마커를 조각 목록으로 바꾼다.
 *
 * 인물 이미지 마커로 인정하려면 **세 조건을 모두** 만족해야 한다.
 * 1. 그 줄 전체가 `[[https://...]]` 일 것
 * 2. 마커 줄 바로 뒤가 빈 줄일 것
 * 3. 빈 줄 다음 줄이 `이름:` 라벨로 시작할 것
 *
 * 장면 이미지는 오리지널 스토리의 프롤로그·시작 상황 사이에 들어가는 그림이라 뒤에 대사가 없다.
 * 1 만 만족하면 이미지로 바꾸고, 뒤따르는 빈 줄은 이미지 블록 간격으로 대신한다.
 *
 * 하나라도 어긋나면 **그 줄을 평문으로 그대로 남긴다.** 조용히 지우면 서버가 보낸 본문이 화면에서
 * 사라진 것을 아무도 알 수 없다.
 */
fun parsePassageSegments(content: String): List<PassageSegment> {
    val normalized = content.replace("\r\n", "\n")
    val markers = findMarkers(normalized)
    if (markers.isEmpty()) {
        return if (content.isEmpty()) emptyList() else listOf(PassageSegment.Text(content))
    }

    val segments = mutableListOf<PassageSegment>()
    var cursor = 0
    for (marker in markers) {
        // 마커 앞 줄바꿈(장면 이미지는 빈 줄까지)은 이미지 블록의 경계다. 남기면 이미지 위 간격이 두 번 들어간다.
        val before = normalized.substring(cursor, marker.start).trimEnd('\n')
        if (before.isNotEmpty()) segments += PassageSegment.Text(before)
        segments += marker.segment
        cursor = marker.end
    }
    val remaining = normalized.substring(cursor)
    if (remaining.isNotEmpty()) segments += PassageSegment.Text(remaining)
    return segments
}

private data class MarkerMatch(
    val start: Int,
    val end: Int,
    val segment: PassageSegment,
)

private fun findMarkers(content: String): List<MarkerMatch> {
    val matches = mutableListOf<MarkerMatch>()
    var lineStart = 0

    while (lineStart <= content.length) {
        val lineBreak = content.indexOf('\n', lineStart)
        val lineEnd = if (lineBreak == -1) content.length else lineBreak
        val imageUrl = MarkerLine.matchEntire(content.substring(lineStart, lineEnd))?.groupValues?.get(1)

        if (imageUrl != null && isAllowedCharacterImageUrl(imageUrl)) {
            markerAt(content, lineStart, lineEnd, imageUrl)?.let { matches += it }
        }

        if (lineBreak == -1) break
        lineStart = lineBreak + 1
    }
    return matches
}

private fun markerAt(
    content: String,
    lineStart: Int,
    lineEnd: Int,
    imageUrl: String,
): MarkerMatch? {
    if (isSceneImageUrl(imageUrl)) {
        val trailingBreaks =
            when {
                content.startsWith("\n\n", lineEnd) -> BLANK_LINE_LENGTH
                content.startsWith("\n", lineEnd) -> 1
                else -> 0
            }
        return MarkerMatch(lineStart, lineEnd + trailingBreaks, PassageSegment.SceneImage(imageUrl))
    }
    if (!content.startsWith("\n\n", lineEnd)) return null
    val speakerLineStart = lineEnd + BLANK_LINE_LENGTH
    val name = speakerName(content, speakerLineStart) ?: return null
    return MarkerMatch(lineStart, speakerLineStart, PassageSegment.CharacterImage(name, imageUrl))
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
