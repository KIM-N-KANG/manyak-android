// 소품의 자리와 크기는 표지 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val Frame = Rect(0.12f, 0.12f, 0.88f, 0.74f)

/** 붓질 하나를 나누는 마디 수. 마디마다 굵기를 바꿔 붓이 눌렸다 들리는 결을 낸다. */
private const val BRUSH_STEPS = 40

/** 물감을 찍은 자리 둘레에 튀는 작은 방울. 찍은 자리에서의 거리와 반지름이다. */
private val Speckles =
    listOf(
        Triple(-0.12f, 0.03f, 0.009f),
        Triple(0.11f, -0.06f, 0.007f),
        Triple(0.08f, 0.10f, 0.011f),
        Triple(-0.07f, -0.09f, 0.006f),
        Triple(0.15f, 0.04f, 0.005f),
    )

/**
 * 액자 그림. 먼 능선을 옅게, 가까운 능선을 진하게 한 번씩 긋고, 하늘에 물감을 찍어 번지게 한 뒤 구석에
 * 서명한다. 아이콘처럼 정해진 모양을 따라 그리지 않고, 붓이 눌렸다 들리는 굵기와 수채 번짐으로 그린다.
 */
internal fun DrawScope.drawPainting(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val visibility = propVisibility(CompletingAct.PAINTING, actMillis)
    withAlpha(visibility.coerceIn(0f, 1f)) {
        scale(0.8f + 0.2f * visibility, pivot = Offset(Frame.center.x, Frame.bottom) * unit) {
            drawCard(Frame, unit, palette)
            val sinceBloom = actMillis - cueStart(Cue.BLOOM)
            val wash = (sinceBloom / 700f).coerceIn(0f, 1f)
            if (wash > 0f) drawWash(wash, unit, palette.brand)
            drawBloom(sinceBloom, unit, palette)
            val farRidge = lerp(palette.paper, palette.brand, 0.5f)
            drawBrushStroke(Stroke1, cueProgress(Cue.STROKE_1, actMillis), 0.04f, farRidge, unit)
            drawBrushStroke(Stroke2, cueProgress(Cue.STROKE_2, actMillis), 0.05f, palette.brand, unit)
            drawBrushStroke(Signature, cueProgress(Cue.SIGNATURE, actMillis), 0.014f, palette.brand, unit)
        }
    }
}

/**
 * 붓질 하나를 [progress] 만큼 긋는다. 굵기는 곡선 위의 자리로 정해져, 긋는 도중에도 완성될 모양 그대로
 * 드러난다. 양 끝은 가늘고 가운데가 가장 굵다.
 */
private fun DrawScope.drawBrushStroke(
    curve: Cubic,
    progress: Float,
    maxWidth: Float,
    color: Color,
    unit: Float,
) {
    if (progress <= 0f) return
    var previous = curve.at(0f)
    for (step in 1..ceil(BRUSH_STEPS * progress).toInt()) {
        val t = min(step / BRUSH_STEPS.toFloat(), progress)
        val point = curve.at(t)
        val middle = (t - 0.5f / BRUSH_STEPS).coerceIn(0f, 1f)
        val pressure = 0.18f + 0.82f * sin(PI.toFloat() * middle).pow(0.7f)
        drawLine(color, previous * unit, point * unit, maxWidth * pressure * unit, StrokeCap.Round)
        previous = point
    }
}

/**
 * 두 능선 사이를 수채 물로 옅게 적신다. 가장자리를 두 붓질이 감싸 네모난 테두리 없이 물이 고인 모양이 된다.
 */
private fun DrawScope.drawWash(
    amount: Float,
    unit: Float,
    color: Color,
) {
    val band =
        Path().apply {
            moveTo(Stroke1.start.x * unit, Stroke1.start.y * unit)
            cubicTo(Stroke1, unit)
            lineTo(Stroke2.start.x * unit, Stroke2.start.y * unit)
            cubicTo(Stroke2, unit)
            close()
        }
    drawPath(
        band,
        Brush.verticalGradient(
            listOf(color.copy(alpha = 0.24f * amount), color.copy(alpha = 0.06f * amount)),
            startY = 0.42f * unit,
            endY = 0.7f * unit,
        ),
    )
}

private fun Path.cubicTo(
    curve: Cubic,
    unit: Float,
) = cubicTo(
    curve.control1.x * unit,
    curve.control1.y * unit,
    curve.control2.x * unit,
    curve.control2.y * unit,
    curve.end.x * unit,
    curve.end.y * unit,
)

/** 물감을 찍은 자리가 둥글게 번지고, 가운데 물감 자국 둘레로 작은 방울이 차례로 튄다. */
private fun DrawScope.drawBloom(
    sinceBloom: Int,
    unit: Float,
    palette: StagePalette,
) {
    val spread = ((sinceBloom - 60) / 700f).coerceIn(0f, 1f)
    if (spread <= 0f) return
    val eased = 1f - (1f - spread).pow(3)
    val center = BloomCenter * unit
    val glow = 0.12f * eased * unit
    drawCircle(
        Brush.radialGradient(
            listOf(palette.brand.copy(alpha = 0.42f), palette.brand.copy(alpha = 0.14f), Color.Transparent),
            center = center,
            radius = glow,
        ),
        radius = glow,
        center = center,
    )
    drawCircle(lerp(palette.paper, palette.brand, 0.55f), 0.035f * eased * unit, center)
    Speckles.forEachIndexed { index, (dx, dy, radius) ->
        val pop = easeOutBack(((sinceBloom - 140 - index * 60) / 260f).coerceIn(0f, 1f))
        if (pop > 0f) {
            drawCircle(
                lerp(palette.paper, palette.brand, 0.6f),
                radius * pop * unit,
                (BloomCenter + Offset(dx, dy)) * unit,
            )
        }
    }
}
