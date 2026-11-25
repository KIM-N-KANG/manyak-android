// 그림의 자리와 굵기는 도화지 격자 안에서 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.chat.room.presentation.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.brushPressure
import app.manyak.designsystem.mascot.drawStroke
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.mix
import app.manyak.designsystem.mascot.pencilPressure

/** 그림 cue 의 획을 긋는 동작이 시작된 뒤 흐른 시간. 다 그렸으면 +∞, 시작 전이면 -∞ 다. */
internal typealias Painted = (RealtimeImageCue) -> Float

internal val Blank: Painted = { Float.NEGATIVE_INFINITY }
internal val Finished: Painted = { Float.POSITIVE_INFINITY }

private val LashTicks = floatArrayOf(0.25f, 0.5f, 0.78f)
private val Sheet =
    Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, Size(PAPER_GRID, GRID_HEIGHT)), CornerRadius(4f))) }
private val Pane = PathParser().parsePathString(PANE).toPath()
private val Moon = Path().apply { addOval(Rect(MoonAt, MOON_RADIUS)) }
private val MoonShadow = Path().apply { addOval(Rect(MoonShadowAt, MOON_SHADOW_RADIUS)) }
private val OutsideMoon =
    Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, Size(PAPER_GRID, GRID_HEIGHT)))
        addOval(Rect(MoonAt, MOON_RADIUS))
    }

/** 처음부터 끝까지 같은 굵기로 칠하는 결. 영역 안으로 잘라 칠하는 밑칠에 쓴다. */
private val Flat: (Float) -> Float = { 1f }

/**
 * 도화지 한 장을 격자 좌표로 그린다. 종이 위에 밤 창가에서 달을 보는 인물을 [painted] 만큼 쌓아 그린다. 밑칠,
 * 밑그림, 인물, 마무리 순서로 겹쳐, 아래 층의 회색 위에 위 층의 선과 옅은 초록이 얹힌다. 빛이 종이 밖으로 새지
 * 않게 층은 종이 안으로 자르고, 테두리는 맨 위에 다시 긋는다.
 */
internal fun DrawScope.drawPainting(
    palette: StagePalette,
    painted: Painted,
) {
    fun progress(cue: RealtimeImageCue) = (painted(cue) / cueMillis(cue)).coerceIn(0f, 1f)
    drawPath(Sheet, palette.paper)
    clipPath(Sheet) {
        drawNight(palette, progress(RealtimeImageCue.NIGHT))
        val sketch = mix(palette.paper, palette.pencil, 0.55f)
        for ((cue, stroke) in listOf(
            RealtimeImageCue.WINDOW to Window,
            RealtimeImageCue.MULLION_V to MullionV,
            RealtimeImageCue.MULLION_H to MullionH,
            RealtimeImageCue.OUTLINE to Outline,
        )) {
            drawStroke(stroke, progress(cue), 1.1f, sketch, ::pencilPressure)
        }
        drawStroke(
            Hair,
            progress(RealtimeImageCue.HAIR),
            6.5f,
            mix(palette.paper, palette.pencil, 0.8f),
            ::brushPressure,
        )
        drawStroke(Face, progress(RealtimeImageCue.FACE), 1.4f, palette.pencil, ::pencilPressure)
        drawEye(palette, painted(RealtimeImageCue.EYE))
        drawBeam(palette, progress(RealtimeImageCue.BEAM))
        val blush = easeOutBack((painted(RealtimeImageCue.CHEEK) / 200f).coerceIn(0f, 1f))
        if (blush > 0f) {
            drawOval(
                mix(palette.paper, palette.brand, 0.3f),
                topLeft = CheekAt - Offset(2.6f * blush, 1.6f * blush),
                size = Size(5.2f * blush, 3.2f * blush),
            )
        }
        drawStroke(
            Sign,
            progress(RealtimeImageCue.SIGN),
            1.1f,
            mix(palette.paper, palette.pencil, 0.6f),
            ::pencilPressure,
        )
    }
    drawPath(Sheet, palette.ink, style = Stroke(width = 1.6f))
}

/** 넘어가는 도화지의 뒷면. 무대에서 튀지 않도록 종이보다 한 톤 짙은 옅은 회색이다. */
internal fun DrawScope.drawPaperBack(palette: StagePalette) {
    drawPath(Sheet, mix(palette.paper, palette.pencil, 0.14f))
    drawPath(Sheet, palette.ink, style = Stroke(width = 1.6f))
}

/**
 * 창 안을 밤 색 지그재그로 쓸어 칠한다. 달 자리는 칠하지 않고 남기고, 그 위에 살짝 비낀 원만 밤 색으로 덮여
 * 초승달이 드러난다.
 */
private fun DrawScope.drawNight(
    palette: StagePalette,
    progress: Float,
) {
    if (progress <= 0f) return
    val color = mix(palette.paper, palette.pencil, 0.32f)
    clipPath(Pane) { clipPath(OutsideMoon) { drawStroke(Night, progress, 16f, color, Flat) } }
    clipPath(Moon) { clipPath(MoonShadow) { drawStroke(Night, progress, 16f, color, Flat) } }
}

/** 감은 눈을 긋고, 다 그은 순간 속눈썹을 아래로 톡톡 찍는다. */
private fun DrawScope.drawEye(
    palette: StagePalette,
    elapsed: Float,
) {
    val millis = cueMillis(RealtimeImageCue.EYE)
    drawStroke(Eye, (elapsed / millis).coerceIn(0f, 1f), 1.2f, palette.pencil, ::pencilPressure)
    val grow = easeOutBack(((elapsed - millis) / 120f).coerceIn(0f, 1f))
    if (grow <= 0f) return
    for (t in LashTicks) {
        val at = Eye.at(t)
        drawLine(palette.pencil, at, at + Offset(-0.5f, 1.6f) * grow, 0.7f, StrokeCap.Round)
    }
}

/** 창 위쪽에서 인물 쪽으로 쏟아지는 달빛. 붓털 끝이 내려가는 높이까지 띠를 한 번에 채워 겹친 자리만 진해지지 않게 한다. */
private fun DrawScope.drawBeam(
    palette: StagePalette,
    progress: Float,
) {
    if (progress <= 0f) return
    val (topLeft, topRight) = BeamTop
    val (bottomLeft, bottomRight) = BeamBottom
    val left = topLeft + (bottomLeft - topLeft) * progress
    val right = topRight + (bottomRight - topRight) * progress
    drawPath(
        Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(right.x, right.y)
            lineTo(left.x, left.y)
            close()
        },
        palette.brand.copy(alpha = palette.brand.alpha * 0.12f),
    )
}
