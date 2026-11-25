// 소품의 자리와 크기는 표지 폭이나 캔버스 격자에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.brushPressure
import app.manyak.designsystem.mascot.drawStroke
import app.manyak.designsystem.mascot.drawWobblyBlob
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.mix
import app.manyak.designsystem.mascot.parseStroke
import app.manyak.designsystem.mascot.pencilPressure
import app.manyak.designsystem.mascot.stageLine
import app.manyak.designsystem.mascot.withAlpha
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 목도리를 두르면 함께 드러나는 목과 어깨선. */
private val PortraitBody = listOf("M33,66 L34,76 Q22,82 16,100", "M45,76 Q58,80 70,98").map(::parseStroke)
private val ScarfTail = parseStroke("M31,77 C25,85 19,84 13,92")

/** 이젤에 세운 세로 캔버스와 그 위의 주인공 인물화. 막 시작에 튀어나오고 막 끝에 사라진다. */
internal fun DrawScope.drawEasel(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val visibility = propVisibility(CompletingAct.PAINTING, actMillis)
    withAlpha(visibility.coerceIn(0f, 1f)) {
        scale(0.8f + 0.2f * visibility, pivot = Offset(0.5f, FLOOR) * unit) {
            stageLine(Offset(0.3f, CANVAS_BOTTOM - 0.02f), Offset(0.26f, FLOOR + 0.01f), 0.012f, palette.ink, unit)
            stageLine(Offset(0.7f, CANVAS_BOTTOM - 0.02f), Offset(0.74f, FLOOR + 0.01f), 0.012f, palette.ink, unit)
            val cell = CANVAS_WIDTH / CANVAS_GRID * unit
            translate(CANVAS_LEFT * unit, CANVAS_TOP * unit) {
                scale(cell, pivot = Offset.Zero) {
                    val canvasSize = Size(CANVAS_GRID, CANVAS_GRID * 4f / 3f)
                    drawRoundRect(palette.paper, size = canvasSize, cornerRadius = CornerRadius(4f))
                    drawRoundRect(
                        palette.ink,
                        size = canvasSize,
                        cornerRadius = CornerRadius(4f),
                        style = Stroke(width = 1.12f),
                    )
                    drawPortrait(actMillis, palette)
                }
            }
        }
    }
}

/**
 * 주인공 인물화를 캔버스 격자 좌표로 그린다. 달을 찍어 번지게 하고, 바람에 날리는 머리를 굵은 붓으로 쓸고,
 * 옆얼굴을 가늘게 긋고 눈을 뜨게 한 뒤, 볼을 찍고 목도리를 두르고 서명한다.
 */
private fun DrawScope.drawPortrait(
    actMillis: Int,
    palette: StagePalette,
) {
    val brand = palette.brand
    val paper = palette.paper
    val pencil = palette.pencil
    val moonSince = actMillis - cueStart(Cue.MOON)
    val spread = ((moonSince - 60) / 700f).coerceIn(0f, 1f)
    if (spread > 0f) {
        val eased = 1f - (1f - spread).pow(3)
        drawWobblyBlob(PortraitMoon, 22f * eased, brand.copy(alpha = 0.13f), 1f)
        drawCircle(mix(paper, brand, 0.3f), 12.5f * eased, PortraitMoon)
        val twinkle = easeOutBack(((moonSince - 300) / 260f).coerceIn(0f, 1f))
        if (twinkle > 0f) drawSparkle(Offset(74f, 15f), 3.2f * twinkle, brand)
    }

    val scarf = cueProgress(Cue.SCARF, actMillis)
    for (stroke in PortraitBody) drawStroke(stroke, scarf, 1.4f, pencil, ::pencilPressure)
    drawStroke(PortraitHair, cueProgress(Cue.HAIR, actMillis), 6f, brand, ::brushPressure)

    val face = cueProgress(Cue.FACE, actMillis)
    drawStroke(PortraitFace, face, 1.5f, pencil, ::pencilPressure)
    val open = easeOutBack(((actMillis - cueStart(Cue.FACE) - cueMillis(Cue.FACE)) / 220f).coerceIn(0f, 1f))
    if (face >= 1f && open > 0f) {
        // 앞을 똑바로 보는 뜬 눈과 눈썹으로, 그림 속 주인공에게 또렷한 표정을 준다.
        drawPath(
            Path().apply {
                moveTo(47.5f, 43.6f)
                quadraticTo(51f, 43.6f - 2.4f * open, 54.5f, 43.6f)
                moveTo(47f, 39.6f)
                quadraticTo(51f, 39.6f - 1.8f * open, 55f, 39.2f)
            },
            pencil,
            style = Stroke(width = 1.3f, cap = StrokeCap.Round),
        )
        drawCircle(pencil, 1.5f * open, Offset(52.4f, 44.4f))
    }

    val blush = easeOutBack(((actMillis - cueStart(Cue.BLUSH)) / 260f).coerceIn(0f, 1f))
    if (blush > 0f) {
        drawOval(
            mix(paper, brand, 0.5f),
            PortraitBlush - Offset(3.6f * blush, 2.2f * blush),
            Size(7.2f * blush, 4.4f * blush),
        )
    }

    drawStroke(PortraitScarf, scarf, 6f, mix(paper, brand, 0.6f), ::brushPressure)
    drawStroke(ScarfTail, (scarf - 0.5f) * 2f, 4.5f, brand, ::brushPressure)
    drawStroke(PortraitSignature, cueProgress(Cue.SIGNATURE, actMillis), 1.3f, brand, ::brushPressure)
}

/** 네 갈래로 뻗은 반짝임 별을 칠한다. */
internal fun DrawScope.drawSparkle(
    at: Offset,
    radius: Float,
    color: Color,
) {
    val path = Path()
    for (corner in 0 until 8) {
        val reach = if (corner % 2 == 0) radius else radius * 0.28f
        val angle = corner * PI.toFloat() / 4f
        val x = at.x + sin(angle) * reach
        val y = at.y - cos(angle) * reach
        if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
