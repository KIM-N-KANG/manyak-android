// 소품의 자리와 크기는 표지 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/** 머리가 카드에 닿는 순간. 머리받기 점프의 한가운데다. */
private const val HEADBUTT_CONTACT = 0.5f

/**
 * 키워드 칩 무리. 차례로 튀어나와 발판이 되고, 마스코트가 밟은 칩은 그 순간 브랜드 색으로 채워지며 통 튄다.
 * 앱의 선택 칩처럼 고른 칩만 채우고 글자 자리를 반전색으로 바꾼다.
 */
internal fun DrawScope.drawKeywordChips(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    withAlpha(1f - cueProgress(Cue.KEYWORDS_LEAVE, actMillis)) {
        KeywordChips.forEachIndexed { index, chip ->
            val enter = propVisibility(CompletingAct.KEYWORDS, actMillis, enterDelay = index * 45)
            val pickedAt = PickedChips.firstOrNull { it.first == index }?.let { cueStart(it.second) }
            val since = pickedAt?.let { actMillis - it } ?: -1
            val picked = since >= 0
            val pop = if (picked) 1f + 0.14f * sin(PI.toFloat() * (since / 240f).coerceIn(0f, 1f)) else 1f
            val rect = Rect(chip.left, chip.top, chip.right, chip.top + CHIP_HEIGHT)
            scale(enter * pop, pivot = rect.center * unit) {
                drawCard(
                    rect = rect,
                    unit = unit,
                    palette = palette,
                    outline = if (picked) palette.brand else palette.ink,
                    fill = if (picked) palette.brand else palette.paper,
                    corner = CHIP_HEIGHT / 2f,
                )
                val word = rect.width * 0.5f
                drawLine(
                    color = if (picked) palette.paper else palette.ink,
                    start = Offset(rect.center.x - word / 2f, rect.center.y) * unit,
                    end = Offset(rect.center.x + word / 2f, rect.center.y) * unit,
                    strokeWidth = 0.018f * unit,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 스토리라인 카드 세 장. 아래에서 차례로 올라오고, 마스코트가 올려다보는 카드는 테두리가 물들며 살짝 뜬다.
 * 머리로 받은 카드는 위로 튕겨 고른 표시가 붙고, 나머지는 흐려지며 가라앉는다.
 */
internal fun DrawScope.drawStorylines(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val pickAt = cueStart(Cue.STORYLINE_PICK) + (cueMillis(Cue.STORYLINE_PICK) * HEADBUTT_CONTACT).toInt()
    val sincePick = actMillis - pickAt
    StorylineLefts.forEachIndexed { index, left ->
        val enter = propVisibility(CompletingAct.STORYLINE, actMillis, enterDelay = index * 90)
        val hover =
            when (index) {
                0 -> sin(PI.toFloat() * cueProgress(Cue.STORYLINE_1_LOOK, actMillis))
                2 -> sin(PI.toFloat() * cueProgress(Cue.STORYLINE_3_LOOK, actMillis))
                else -> 0f
            }
        val chosen = if (index == PICKED_STORYLINE && sincePick >= 0) 1f else 0f
        val dimmed = if (index != PICKED_STORYLINE) (sincePick / 300f).coerceIn(0f, 1f) else 0f
        val bump = chosen * sin(PI.toFloat() * (sincePick / 260f).coerceIn(0f, 1f)) * 0.05f
        val rise = (1f - enter) * 0.12f - hover * 0.015f - bump + dimmed * 0.03f
        withAlpha(enter.coerceIn(0f, 1f) * (1f - 0.65f * dimmed)) {
            translate(top = rise * unit) {
                drawStorylineCard(
                    rect = Rect(left, STORYLINE_TOP, left + STORYLINE_WIDTH, STORYLINE_BOTTOM),
                    unit = unit,
                    palette = palette,
                    highlight = maxOf(hover, chosen),
                )
                if (chosen > 0f) {
                    val badge = easeOutBack((sincePick / 260f).coerceIn(0f, 1f))
                    drawCheckBadge(
                        Offset(left + STORYLINE_WIDTH - 0.02f, STORYLINE_TOP + 0.02f),
                        0.045f * badge,
                        unit,
                        palette,
                    )
                }
            }
        }
    }
}

/** 제목 줄 하나와 본문 줄 넷. [highlight] 만큼 테두리와 제목이 브랜드 색으로 물든다. */
private fun DrawScope.drawStorylineCard(
    rect: Rect,
    unit: Float,
    palette: StagePalette,
    highlight: Float,
) {
    drawCard(rect, unit, palette, outline = lerp(palette.ink, palette.brand, highlight))
    val inset = 0.04f
    val inner = rect.width - inset * 2
    val lines = floatArrayOf(0.62f, 1f, 0.82f, 1f, 0.7f)
    lines.forEachIndexed { index, width ->
        val title = index == 0
        val y = rect.top + 0.07f + index * 0.06f + if (title) 0f else 0.02f
        drawLine(
            color = if (title) lerp(palette.ink, palette.brand, highlight) else palette.ink,
            start = Offset(rect.left + inset, y) * unit,
            end = Offset(rect.left + inset + inner * width, y) * unit,
            strokeWidth = (if (title) 0.024f else 0.014f) * unit,
            cap = StrokeCap.Round,
        )
    }
}

/** 고른 카드 모서리에 붙는 체크 표시. */
private fun DrawScope.drawCheckBadge(
    center: Offset,
    radius: Float,
    unit: Float,
    palette: StagePalette,
) {
    if (radius <= 0f) return
    drawCircle(palette.brand, radius * unit, center * unit)
    val check =
        Path().apply {
            moveTo((center.x - radius * 0.42f) * unit, (center.y + radius * 0.02f) * unit)
            lineTo((center.x - radius * 0.1f) * unit, (center.y + radius * 0.34f) * unit)
            lineTo((center.x + radius * 0.45f) * unit, (center.y - radius * 0.3f) * unit)
        }
    drawPath(
        check,
        palette.paper,
        style = Stroke(radius * 0.26f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
