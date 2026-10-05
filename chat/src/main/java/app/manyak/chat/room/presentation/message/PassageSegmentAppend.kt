package app.manyak.chat.room.presentation.message

import app.manyak.designsystem.component.isAllowedCharacterImageUrl
import app.manyak.designsystem.text.PassageSegment

/**
 * 스트리밍 토큰을 마지막 텍스트 조각에 이어 붙인다.
 *
 * 조각을 새로 만들지 않고 이어 붙이는 이유는 한 문단이 조각 수십 개로 쪼개지면 강조 마커가
 * 조각 경계에서 끊겨 파싱되지 않기 때문이다.
 */
fun List<PassageSegment>.appendText(content: String): List<PassageSegment> {
    if (content.isEmpty()) return this
    val last = lastOrNull()
    return if (last is PassageSegment.Text) {
        dropLast(1) + PassageSegment.Text(last.content + content)
    } else {
        this + PassageSegment.Text(content)
    }
}

/**
 * 스트리밍 중 도착한 인물 이미지를 지금 위치에 끼운다.
 *
 * **직전 텍스트 끝의 줄바꿈을 모두 지운다** — 이미지 블록의 경계이지 본문의 빈 줄이 아니다. 대사 앞
 * 이미지는 문단 사이 빈 줄(`\n\n`) 뒤에 오는데, `Text` 는 끝에 남은 줄바꿈 하나도 빈 줄로 그려 이미지
 * 위 간격이 한 줄만큼 벌어진다. 저장 본문 파서와 같은 규칙이다. 이름이 비었거나 허용하지 않는 URL 이면
 * 아무것도 하지 않는다.
 */
fun List<PassageSegment>.appendCharacterImage(
    name: String,
    imageUrl: String,
): List<PassageSegment> {
    if (name.isBlank() || !isAllowedCharacterImageUrl(imageUrl)) return this

    val last = lastOrNull()
    val head =
        if (last is PassageSegment.Text && last.content.endsWith("\n")) {
            val trimmed = last.content.trimEnd('\n')
            if (trimmed.isEmpty()) dropLast(1) else dropLast(1) + PassageSegment.Text(trimmed)
        } else {
            this
        }
    return head + PassageSegment.CharacterImage(name = name.trim(), imageUrl = imageUrl)
}
