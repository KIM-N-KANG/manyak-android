package app.manyak.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.manyak.core.ui.theme.ManyakTheme

/** 텍스트를 스타일별로 나눈 조각. */
data class TextSegment(
    val text: String,
    /** 단일 *...* — 내레이션·속마음(보조 색상). */
    val emphasis: Boolean = false,
    /** 이중 **...** — 볼드. */
    val bold: Boolean = false,
)

/** `**...**`(볼드)를 먼저, 그다음 이중 '*'가 아닌 단일 `*...*`(강조)를 매칭한다. */
private val segmentPattern = Regex("""\*\*([^*\n]+?)\*\*|(?<!\*)\*(?!\*)([^*\n]+?)\*(?!\*)""")

/** 서사 텍스트를 마크다운 유사 문법에 따라 세그먼트로 나눈다. 규칙은 웹 클라이언트와 같다. */
fun parseTextSegments(line: String): List<TextSegment> {
    val segments = mutableListOf<TextSegment>()
    var lastIndex = 0

    for (match in segmentPattern.findAll(line)) {
        if (match.range.first > lastIndex) {
            segments += TextSegment(text = line.substring(lastIndex, match.range.first))
        }

        val boldGroup = match.groups[1]
        segments +=
            if (boldGroup != null) {
                TextSegment(text = boldGroup.value, bold = true)
            } else {
                TextSegment(text = match.groupValues[2], emphasis = true)
            }

        lastIndex = match.range.last + 1
    }

    if (lastIndex < line.length) {
        segments += TextSegment(text = line.substring(lastIndex))
    }

    return segments
}

/** 파싱한 세그먼트를 스타일 있는 문자열로 조립한다. 강조는 보조 색으로, 볼드는 굵기로만 구분한다. */
@Composable
fun storyAnnotatedString(text: String): AnnotatedString = storyAnnotatedString(text, ManyakTheme.colors.textSubtlest)

/**
 * 색을 직접 받는 변형.
 *
 * `@Composable` 이 아니라 **캐시할 수 있다** — 스트리밍처럼 본문이 계속 자라는 자리에서는 매
 * recomposition 마다 전문을 다시 파싱하면 비용이 길이의 제곱이 된다. 부르는 쪽이
 * `remember(text, color)` 로 감싼다.
 */
fun storyAnnotatedString(
    text: String,
    emphasisColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        parseTextSegments(text).forEach { segment ->
            when {
                segment.bold ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment.text) }

                segment.emphasis ->
                    withStyle(SpanStyle(color = emphasisColor)) { append(segment.text) }

                else -> append(segment.text)
            }
        }
    }
