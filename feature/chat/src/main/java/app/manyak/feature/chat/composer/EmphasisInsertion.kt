package app.manyak.feature.chat.composer

/** 마커를 넣은 뒤의 값과 선택 범위. 범위는 마커 **안쪽** 글자를 가리킨다. */
data class EmphasisInsertion(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * 고른 구간을 `*...*` 로 감싼다. 고른 것이 없으면 커서 자리에 빈 마커를 넣고 커서를 그 사이에 둔다.
 *
 * 감싼 뒤 선택을 마커 안쪽에 그대로 두는 이유는 이어서 지우거나 고쳐 쓰는 동작이 자연스럽게
 * 이어지게 하기 위함이다.
 */
fun insertEmphasisMarkers(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): EmphasisInsertion {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val selected = text.substring(start, end)
    return EmphasisInsertion(
        text = text.substring(0, start) + "*" + selected + "*" + text.substring(end),
        selectionStart = start + 1,
        selectionEnd = start + 1 + selected.length,
    )
}
