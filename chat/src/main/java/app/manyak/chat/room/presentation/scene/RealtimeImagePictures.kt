// 그림의 자리와 굵기는 도화지 격자 안에서 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.chat.room.presentation.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.brushPressure
import app.manyak.designsystem.mascot.drawStroke
import app.manyak.designsystem.mascot.drawWobblyBlob
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.mix
import app.manyak.designsystem.mascot.parseStroke
import app.manyak.designsystem.mascot.pencilPressure
import app.manyak.designsystem.mascot.smooth
import kotlin.math.sin

internal enum class Picture { SKETCH, WATERCOLOR, CONSTELLATION }

/** 그림이 어디까지 그려졌는지. 다 그린 그림은 모든 cue 가 오래전에 끝난 것으로 본다. */
internal class Reveal(
    /** cue 동작이 시작된 뒤 흐른 시간. */
    val elapsed: (RealtimeImageCue) -> Float,
    /** 무대가 시작된 뒤 흐른 시간. 별이 반짝일 때 쓴다. */
    val millis: Int,
) {
    fun progress(cue: RealtimeImageCue) = (elapsed(cue) / cueMillis(cue)).coerceIn(0f, 1f)
}

internal val Done = Reveal({ Float.POSITIVE_INFINITY }, 0)

internal const val GRID_HEIGHT = PAPER_GRID * 4f / 3f

/** 도화지 한 장을 격자 좌표로 그리고, 종이 위에 [picture] 를 [reveal] 만큼 그린다. [night] 면 뒤집은 면이다. */
internal fun DrawScope.drawSheet(
    palette: StagePalette,
    picture: Picture?,
    reveal: Reveal,
    night: Boolean = picture == Picture.CONSTELLATION,
) {
    // 뒤집은 면은 무대에서 튀지 않도록 종이보다 한 톤 짙은 옅은 회색이다.
    drawRoundRect(
        color = if (night) mix(palette.paper, palette.pencil, 0.14f) else palette.paper,
        size = Size(PAPER_GRID, GRID_HEIGHT),
        cornerRadius = CornerRadius(4f),
    )
    drawRoundRect(
        color = palette.ink,
        size = Size(PAPER_GRID, GRID_HEIGHT),
        cornerRadius = CornerRadius(4f),
        style = Stroke(width = 1.6f),
    )
    when (picture) {
        Picture.SKETCH -> drawSketch(palette, reveal)
        Picture.WATERCOLOR -> drawWatercolor(palette, reveal)
        Picture.CONSTELLATION -> drawConstellation(palette, reveal)
        null -> Unit
    }
}

private val LashTicks = floatArrayOf(0.25f, 0.5f, 0.78f)

/**
 * 연필 크로키. 한 줄 윤곽은 진한 선 옆에 옅은 선을 하나 더 비껴 그어 빠르게 휘갈긴 결을 내고, 머리카락은 옅은
 * 지그재그로 채운다.
 */
private fun DrawScope.drawSketch(
    palette: StagePalette,
    reveal: Reveal,
) {
    val contour = reveal.progress(RealtimeImageCue.CONTOUR)
    val ghost = mix(palette.paper, palette.pencil, 0.4f)
    drawStroke(SketchContour, contour, 0.8f, ghost, ::pencilPressure) { length -> sin(length * 0.21f) * 1.1f }
    drawStroke(SketchContour, contour, 1.7f, palette.pencil, ::pencilPressure)
    drawStroke(
        SketchHatch,
        reveal.progress(RealtimeImageCue.HATCH),
        1.1f,
        mix(palette.paper, palette.pencil, 0.6f),
        ::pencilPressure,
    )
    val lash = reveal.progress(RealtimeImageCue.LASH)
    drawStroke(SketchLash, lash, 1.2f, palette.pencil, ::pencilPressure)
    if (lash < 1f) return
    // 속눈썹은 눈매를 다 그은 순간 아래로 톡톡 찍힌다.
    val grow = easeOutBack((reveal.elapsed(RealtimeImageCue.LASH) / 260f - 0.7f).coerceIn(0f, 1f))
    if (grow <= 0f) return
    for (t in LashTicks) {
        val at = SketchLash.at(t)
        drawLine(palette.pencil, at, at + Offset(-0.6f * grow, 2f * grow), 0.8f, StrokeCap.Round)
    }
}

/** 목도리를 두르면 함께 드러나는 목과 어깨선. */
private val WatercolorBody =
    listOf("M41,61 L41,67", "M52,60 L52,67", "M28,73 Q18,80 14,98", "M66,73 Q74,80 76,98").map(::parseStroke)
private val ScarfTail = parseStroke("M58,75 C62,84 58,92 63,101")
private val Speckles =
    listOf(Triple(69f, 22f, 1.5f), Triple(74f, 29f, 1f), Triple(14f, 28f, 1.2f), Triple(20f, 70f, 0.9f))

/**
 * 수채 인물. 물감을 찍은 자리가 둥글게 번진 위에 굵은 붓으로 단발을 쓸고, 가는 선으로 얼굴을, 볼 터치와 목도리를
 * 얹는다.
 */
private fun DrawScope.drawWatercolor(
    palette: StagePalette,
    reveal: Reveal,
) {
    val brand = palette.brand
    val paper = palette.paper
    val lead = palette.pencil
    val halo = reveal.elapsed(RealtimeImageCue.HALO)
    val spread = smooth((halo - 60f) / 700f)
    if (spread > 0f) {
        drawWobblyBlob(WatercolorHalo, 28f * spread, brand.copy(alpha = 0.07f), 0f)
        drawWobblyBlob(Offset(48f, 40f), 19f * spread, brand.copy(alpha = 0.05f), 2f)
        Speckles.forEachIndexed { index, (x, y, radius) ->
            val pop = easeOutBack(((halo - 160f - index * 70f) / 240f).coerceIn(0f, 1f))
            if (pop > 0f) drawCircle(mix(paper, lead, 0.3f), radius * pop, Offset(x, y))
        }
    }

    val scarf = reveal.progress(RealtimeImageCue.SCARF)
    if (scarf > 0f) {
        drawPath(
            Path().apply {
                moveTo(27f, 71f)
                cubicTo(38f, 64f, 56f, 64f, 67f, 70f)
                cubicTo(60f, 77f, 44f, 79f, 30f, 76f)
                close()
            },
            brand.copy(alpha = 0.1f * scarf),
        )
    }
    for (stroke in WatercolorBody) drawStroke(stroke, scarf, 1.1f, lead, ::pencilPressure)

    val face = reveal.progress(RealtimeImageCue.FACE)
    drawStroke(WatercolorFace, face, 1.2f, lead, ::pencilPressure)
    val open = easeOutBack((reveal.elapsed(RealtimeImageCue.FACE) / 200f - 2.1f).coerceIn(0f, 1f))
    if (face >= 1f && open > 0f) drawSmile(lead, open)

    val blush = easeOutBack((reveal.elapsed(RealtimeImageCue.CHEEK) / 260f).coerceIn(0f, 1f))
    if (blush > 0f) {
        val cheek = mix(paper, brand, 0.3f)
        drawOval(cheek, WatercolorCheek - Offset(4f * blush, 2.6f * blush), Size(8f * blush, 5.2f * blush))
        drawOval(cheek, Offset(52f - 3f * blush, 49f - 2f * blush), Size(6f * blush, 4f * blush))
    }

    drawStroke(WatercolorBob, reveal.progress(RealtimeImageCue.BOB), 5.5f, mix(paper, lead, 0.75f), ::brushPressure)
    drawStroke(WatercolorScarf, scarf, 5f, mix(paper, brand, 0.3f), ::brushPressure)
    drawStroke(ScarfTail, (scarf - 0.5f) * 2f, 4f, mix(paper, brand, 0.4f), ::brushPressure)
}

/** 첫 그림의 고요히 감은 눈과 달리 웃으며 감은 눈과 입꼬리로 밝은 표정을 준다. [open] 만큼 휜다. */
private fun DrawScope.drawSmile(
    color: Color,
    open: Float,
) {
    drawPath(
        Path().apply {
            moveTo(33f, 41f)
            quadraticTo(35f, 41f - 2.4f * open, 37f, 41f)
            moveTo(45f, 41.2f)
            quadraticTo(47.5f, 41.2f - 2.8f * open, 50f, 41.2f)
            moveTo(35f, 53f)
            quadraticTo(37.5f, 53f + 2.2f * open, 40f, 52.6f)
        },
        color,
        style = Stroke(width = 1.3f, cap = StrokeCap.Round),
    )
}

private val FaintStars =
    listOf(Offset(10f, 10f), Offset(76f, 12f), Offset(9f, 72f), Offset(40f, 104f), Offset(77f, 96f), Offset(70f, 84f))
private val ConstellationTotal = ConstellationLines.sumOf { it.total.toDouble() }.toFloat()

/**
 * 뒤집은 종이 위 별자리 인물. 마스코트가 밟은 자리마다 별이 찍히고, 다 찍으면 별을 이어 옆얼굴과 포니테일이
 * 드러나며 눈 별이 깜빡 떠오른다.
 */
private fun DrawScope.drawConstellation(
    palette: StagePalette,
    reveal: Reveal,
) {
    val light = palette.pencil

    fun twinkle(index: Int) = 0.7f + 0.3f * sin(reveal.millis / 190f + index * 1.7f)

    FaintStars.forEachIndexed { index, at -> drawCircle(light.copy(alpha = 0.35f * twinkle(index)), 0.7f, at) }

    // 한 선을 반투명하게 한 번에 그어야 이음매가 진해지지 않는다.
    var drawn = reveal.progress(RealtimeImageCue.CONNECT) * ConstellationTotal
    for (stroke in ConstellationLines) {
        if (drawn <= 0f) break
        val portion = (drawn / stroke.total).coerceAtMost(1f)
        drawn -= stroke.total
        val path = Path().apply { moveTo(stroke.points[0].x, stroke.points[0].y) }
        for (i in 1 until stroke.points.size) {
            if (stroke.lengths[i] > portion * stroke.total) {
                val at = stroke.at(portion)
                path.lineTo(at.x, at.y)
                break
            }
            path.lineTo(stroke.points[i].x, stroke.points[i].y)
        }
        drawPath(path, light.copy(alpha = 0.45f), style = Stroke(0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    Stars.forEachIndexed { index, star ->
        val since = reveal.elapsed(star.cue) - cueMillis(star.cue)
        if (since >= 0f) drawStar(star.at, if (star.big) 2.2f else 1.5f, since, twinkle(index), palette)
    }
    val eye = reveal.elapsed(RealtimeImageCue.CONNECT) - cueMillis(RealtimeImageCue.CONNECT) * 0.55f
    if (eye >= 0f) drawStar(ConstellationEye, 1.3f, eye, 1f, palette)
}

/** 별 하나. 찍히는 순간 크게 빛났다가 자리를 잡고, 큰 별은 십자 반짝임을 단다. */
private fun DrawScope.drawStar(
    at: Offset,
    radius: Float,
    since: Float,
    twinkle: Float,
    palette: StagePalette,
) {
    val color = palette.pencil
    val pop = easeOutBack((since / 220f).coerceIn(0f, 1f))
    val flash = (1f - since / 360f).coerceAtLeast(0f)
    val glow = radius * (2.2f + 2.5f * flash) * pop
    if (glow > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.25f * twinkle), color.copy(alpha = 0f)), at, glow),
            glow,
            at,
        )
    }
    drawCircle(color, radius * pop, at)
    if (radius < 2f) return
    val ray = radius * (1.8f + 1.4f * flash) * pop * twinkle
    drawLine(color, at - Offset(ray, 0f), at + Offset(ray, 0f), 0.6f, StrokeCap.Round)
    drawLine(color, at - Offset(0f, ray), at + Offset(0f, ray), 0.6f, StrokeCap.Round)
}
