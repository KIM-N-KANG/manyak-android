// 소품의 자리와 크기는 무대 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber", "TooManyFunctions")

package app.manyak.chat.room.presentation.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.manyak.designsystem.component.drawManyakMascot
import app.manyak.designsystem.mascot.BRUSH_TILT
import app.manyak.designsystem.mascot.BrushGrip
import app.manyak.designsystem.mascot.HAT_TILT
import app.manyak.designsystem.mascot.HatAnchor
import app.manyak.designsystem.mascot.MascotPose
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.blinkOpenness
import app.manyak.designsystem.mascot.drawBeret
import app.manyak.designsystem.mascot.drawBrush
import app.manyak.designsystem.mascot.drawStageDots
import app.manyak.designsystem.mascot.drawStageShadow
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.inViewport
import app.manyak.designsystem.mascot.mix
import app.manyak.designsystem.mascot.onBody
import app.manyak.designsystem.mascot.rememberStageMillis
import app.manyak.designsystem.mascot.smooth
import app.manyak.designsystem.mascot.stageLine
import app.manyak.designsystem.mascot.stagePalette
import app.manyak.designsystem.mascot.withAlpha
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 실시간 이미지를 기다리는 4:3 장면 자리. 마스코트가 화가 베레모를 쓰고 붓으로 4:3 가로 도화지에 창가 인물화
 * 한 장을 천천히 그리는 막을 연기한다. 안무는 [realtimeImageMoment] 가 정하고 여기서는 그리기만 한다.
 *
 * 자리는 받은 폭을 그대로 쓰되 안의 연기는 [CONTENT_SCALE] 로 줄여 대화 흐름에서 튀지 않게 한다. 바탕 점은
 * 줄이지 않는다.
 */
@Composable
internal fun RealtimeImageStage(modifier: Modifier = Modifier) {
    val millis by rememberStageMillis(RealtimeImageStillMillis)
    val palette = stagePalette()
    val dotGap = ManyakTheme.sizes.generationDotGap
    val dotRadius = ManyakTheme.sizes.generationDotRadius
    Canvas(modifier = modifier) {
        drawStageDots(palette.ink.copy(alpha = 0.25f), dotGap.toPx(), dotRadius.toPx())
        scale(CONTENT_SCALE) { drawScene(millis, size.width, palette) }
    }
}

private const val CONTENT_SCALE = 0.72f
private val PaperTopLeft = Offset(PAPER_LEFT, PAPER_TOP)
private val PaperTopCenter = Offset((PAPER_LEFT + PAPER_RIGHT) / 2f, PAPER_TOP)

/** 이젤과 도화지, 물그릇, 마스코트, 베레모와 붓, 막에 맞는 소품을 차례로 얹는다. */
private fun DrawScope.drawScene(
    millis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val (act, actMillis, pose) = realtimeImageMoment(millis)
    drawEasel(unit, palette)
    drawEaselPaper(unit, palette, act, actMillis)
    drawWater(unit, palette)
    // 도화지 위에 올라서 있을 때는 바닥 그림자가 허공에 뜬 것처럼 보여 도화지 가까이에서 옅게 지운다.
    val shadow = ((PAPER_LEFT - 0.01f - (pose.x + MASCOT_HALF_WIDTH)) / 0.05f).coerceIn(0f, 1f)
    drawStageShadow(pose, unit, MASCOT_SIZE, FLOOR, palette.ink.copy(alpha = shadow))
    drawManyakMascot(
        feet = Offset(pose.x, pose.y) * unit,
        size = MASCOT_SIZE * unit,
        color = palette.brand,
        scaleX = pose.scaleX,
        scaleY = pose.scaleY,
        rotationDegrees = pose.rotation,
        look = pose.look,
        eyeOpenness = blinkOpenness(millis) * (1f - 0.9f * pose.squint),
        eyes = pose.eyes,
    )
    drawGear(unit, palette, act, actMillis, pose)
    drawBowl(unit, palette)
    when (act) {
        RealtimeImageAct.WASH -> drawSpeedLines(unit, palette, actMillis, pose)
        RealtimeImageAct.RELIEF -> {
            drawRelief(unit, palette, actMillis, pose)
            drawSplash(unit, palette, cueProgress(RealtimeImageCue.RINSE_1, actMillis))
        }
        RealtimeImageAct.DAYDREAM -> {
            drawDaydream(unit, palette, actMillis)
            drawSplash(unit, palette, cueProgress(RealtimeImageCue.RINSE_2, actMillis))
        }
        RealtimeImageAct.PAGE_TURN -> drawSplash(unit, palette, cueProgress(RealtimeImageCue.RINSE_3, actMillis))
        RealtimeImageAct.SHOWCASE -> drawSparkles(unit, palette, actMillis)
        else -> Unit
    }
}

/**
 * 도화지 격자 좌표계로 옮겨 그린다. 무대의 [anchor] 자리에 격자의 [anchorGrid] 자리가 오도록 놓고, 그 자리를
 * 축으로 돌리고 늘린다. 배율 1 은 이젤 도화지 크기다.
 */
private inline fun DrawScope.onGrid(
    unit: Float,
    anchor: Offset,
    anchorGrid: Offset,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    block: DrawScope.() -> Unit,
) {
    val cell = PAPER_WIDTH / PAPER_GRID * unit
    translate(anchor.x * unit, anchor.y * unit) {
        rotate(rotation, pivot = Offset.Zero) {
            scale(cell * scaleX, cell * scaleY, pivot = Offset.Zero) {
                translate(-anchorGrid.x, -anchorGrid.y) { block() }
            }
        }
    }
}

/** 이젤 다리와 받침. 종이를 넘겨도 이젤은 남는다. */
private fun DrawScope.drawEasel(
    unit: Float,
    palette: StagePalette,
) {
    val ledge = PAPER_BOTTOM - 0.004f
    stageLine(Offset(PAPER_LEFT + 0.07f, ledge), Offset(PAPER_LEFT + 0.045f, FLOOR + 0.004f), 0.007f, palette.ink, unit)
    stageLine(
        Offset(PAPER_RIGHT - 0.07f, ledge),
        Offset(PAPER_RIGHT - 0.045f, FLOOR + 0.004f),
        0.007f,
        palette.ink,
        unit,
    )
    stageLine(Offset(PAPER_LEFT + 0.02f, ledge), Offset(PAPER_RIGHT - 0.02f, ledge), 0.01f, palette.ink, unit)
}

/**
 * 이젤에 놓인 도화지. 지난 막까지 그린 층 위에 지금 막의 획을 그린 만큼 얹는다. 종이를 넘기는 막에서는 밑의 새
 * 종이 위로 다 그린 종이가 위쪽 가장자리를 축으로 넘어가며 뒷면을 보이고 옅어진다.
 */
private fun DrawScope.drawEaselPaper(
    unit: Float,
    palette: StagePalette,
    act: RealtimeImageAct,
    actMillis: Int,
) {
    if (act != RealtimeImageAct.PAGE_TURN) {
        onGrid(unit, PaperTopLeft, Offset.Zero, 1f, 1f, 0f) {
            drawPainting(palette) { cue -> paintedFor(cue, act, actMillis) }
        }
        return
    }
    onGrid(unit, PaperTopLeft, Offset.Zero, 1f, 1f, 0f) { drawPainting(palette, Blank) }
    val turn = smooth(cueProgress(RealtimeImageCue.TURN, actMillis) / 0.9f)
    val fold = cos(PI.toFloat() * turn)
    val front = fold >= 0f
    val lifted = PaperTopCenter - Offset(0f, 0.015f * sin(PI.toFloat() * turn))
    withAlpha(1f - smooth((turn - 0.55f) / 0.4f)) {
        onGrid(
            unit,
            lifted,
            Offset(PAPER_GRID / 2f, 0f),
            1f,
            if (front) fold.coerceAtLeast(0.02f) else fold.coerceAtMost(-0.02f),
            0f,
        ) {
            if (front) drawPainting(palette, Finished) else drawPaperBack(palette)
        }
    }
}

/** 물그릇의 물 표면. 붓보다 먼저 그려 붓털 끝이 물 위에 비치게 한다. */
private fun DrawScope.drawWater(
    unit: Float,
    palette: StagePalette,
) {
    val halfWidth = BOWL_HALF_WIDTH * 0.88f * unit
    val depth = 0.004f * unit
    drawOval(
        mix(palette.paper, palette.pencil, 0.18f),
        topLeft = Offset(BowlX * unit - halfWidth, (BOWL_RIM + 0.002f) * unit - depth),
        size = Size(halfWidth * 2f, depth * 2f),
    )
}

/** 발치의 얕은 물그릇 몸통. 붓 뒤에 그려 헹구는 붓털 끝이 물에 잠겨 가려지게 한다. */
private fun DrawScope.drawBowl(
    unit: Float,
    palette: StagePalette,
) {
    val half = BOWL_HALF_WIDTH
    drawPath(
        Path().apply {
            moveTo((BowlX - half) * unit, BOWL_RIM * unit)
            quadraticTo((BowlX - half * 0.9f) * unit, FLOOR * unit, (BowlX - half * 0.55f) * unit, FLOOR * unit)
            lineTo((BowlX + half * 0.55f) * unit, FLOOR * unit)
            quadraticTo((BowlX + half * 0.9f) * unit, FLOOR * unit, (BowlX + half) * unit, BOWL_RIM * unit)
            close()
        },
        mix(palette.paper, palette.ink, 0.35f),
    )
    stageLine(Offset(BowlX - half, BOWL_RIM), Offset(BowlX + half, BOWL_RIM), 0.004f, palette.ink, unit)
}

/** 붓털 물감을 테마 색으로 바꾼다. 밑그림은 연필 회색, 밑칠과 인물은 회색, 마무리는 옅은 초록이다. */
private fun paint(
    palette: StagePalette,
    color: PaintColor,
): Color =
    when (color) {
        PaintColor.PENCIL -> mix(palette.paper, palette.pencil, 0.55f)
        PaintColor.GRAY -> mix(palette.paper, palette.pencil, 0.32f)
        PaintColor.GREEN -> mix(palette.paper, palette.brand, 0.45f)
    }

/** 붓 회전에서 붓털이 휘는 정도. 끌릴 만큼 살짝 기울 때만 휘고, 치켜들거나 돌릴 때는 곧게 둔다. */
private fun bendOf(brush: Float): Float =
    -(brush / 25f).coerceIn(-1f, 1f) * (1f - ((abs(brush) - 30f) / 20f).coerceIn(0f, 1f))

/**
 * 화가 베레모와 붓. 준비 막에서 베레모는 하늘에서 흔들리며 떨어져 머리에 얹히고, 바닥에 누운 붓은 점프에 맞춰
 * 한 바퀴 돌며 손에 들어온다. 그 뒤로 붓은 몸을 따라 기울고, 자세의 붓 회전만큼 쥔 자리를 축으로 더 돌며,
 * 누르면 붓털이 퍼지고 끌리면 휜다.
 */
private fun DrawScope.drawGear(
    unit: Float,
    palette: StagePalette,
    act: RealtimeImageAct,
    actMillis: Int,
    pose: MascotPose,
) {
    val gearingUp = act == RealtimeImageAct.GEAR_UP
    val fall = if (gearingUp) cueProgress(RealtimeImageCue.HAT, actMillis) else 1f
    val hat = pose.onBody(HatAnchor, MASCOT_SIZE)
    inViewport(
        unit,
        MASCOT_SIZE,
        Offset(hat.x, hat.y - 0.62f * (1f - fall * fall)),
        HAT_TILT + pose.rotation + 22f * sin(fall * PI.toFloat() * 2.5f) * (1f - fall),
        pose.scaleX,
    ) { drawBeret(palette, muted = true) }

    val held = pose.onBody(BrushGrip, MASCOT_SIZE)
    val heldAngle = BRUSH_TILT + pose.rotation + pose.brush
    val catchMillis = cueMillis(RealtimeImageCue.BRUSH) * BRUSH_CATCH
    val flight = if (gearingUp) ((actMillis - cueStart(RealtimeImageCue.BRUSH)) / catchMillis).coerceIn(0f, 1f) else 1f
    val grip =
        if (flight >= 1f) {
            held
        } else {
            BrushOnFloor + (held - BrushOnFloor) * flight - Offset(0f, 0.08f * sin(PI.toFloat() * flight))
        }
    val angle = if (flight >= 1f) heldAngle else -450f + (heldAngle + 450f) * flight
    inViewport(unit, MASCOT_SIZE, grip, angle, 1f) {
        drawBrush(palette, paint(palette, paintColorAt(act, actMillis)), pose.press, bendOf(pose.brush))
    }
}

/** 소품을 몸에 붙여 그릴 때 쓰는 몸 가운데. */
private fun MascotPose.center() = Offset(x, y - MASCOT_HEIGHT * scaleY * 0.55f)

/** 지그재그로 쓸어 칠하는 동안 마스코트 뒤로 짧은 속도 선이 따라붙는다. */
private fun DrawScope.drawSpeedLines(
    unit: Float,
    palette: StagePalette,
    actMillis: Int,
    pose: MascotPose,
) {
    val progress = cueProgress(RealtimeImageCue.NIGHT, actMillis)
    if (progress <= 0.04f || progress >= 0.96f) return
    val along = Night.at(progress) - Night.at(progress - 0.06f)
    val back = -along / (along.getDistance().takeIf { it > 0f } ?: 1f)
    val center = pose.center()
    for (side in listOf(-1f, 1f)) {
        val start = center + back * 0.07f + Offset(-back.y, back.x) * (side * 0.022f)
        stageLine(start, start + back * 0.03f, 0.006f, palette.ink, unit)
    }
}

/** 붓을 헹구는 동안 물그릇에서 물방울이 세 번 튀어 오른다. */
private fun DrawScope.drawSplash(
    unit: Float,
    palette: StagePalette,
    progress: Float,
) {
    val color = mix(palette.paper, palette.pencil, 0.35f)
    listOf(-1f, 1f, 0.4f).forEachIndexed { index, side ->
        val t = ((progress - 0.25f - index * 0.12f) / 0.4f).coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) return@forEachIndexed
        val at = Offset(BowlX + side * 0.02f * t, BOWL_RIM - 0.035f * 4f * t * (1f - t))
        drawCircle(color, 0.0035f * (1f - 0.5f * t) * unit, at * unit)
    }
}

/** 완성한 그림을 감상하는 동안 도화지 네 모서리에 반짝임이 차례로 튀어나왔다 사라진다. */
private fun DrawScope.drawSparkles(
    unit: Float,
    palette: StagePalette,
    actMillis: Int,
) {
    val corners =
        listOf(
            Offset(PAPER_LEFT - 0.012f, PAPER_TOP - 0.012f),
            Offset(PAPER_RIGHT + 0.012f, PAPER_TOP + 0.03f),
            Offset(PAPER_RIGHT + 0.008f, PAPER_BOTTOM - 0.02f),
            Offset(PAPER_LEFT - 0.01f, PAPER_BOTTOM - 0.05f),
        )
    corners.forEachIndexed { index, at ->
        val elapsed = actMillis - cueStart(RealtimeImageCue.SHOWCASE) - index * 90
        val size = easeOutBack((elapsed / 220f).coerceIn(0f, 1f)) * (1f - smooth((elapsed - 380) / 180f))
        if (size <= 0f) return@forEachIndexed
        val reach = 0.018f * size
        stageLine(at - Offset(reach, 0f), at + Offset(reach, 0f), 0.005f, palette.pencil, unit)
        stageLine(at - Offset(0f, reach), at + Offset(0f, reach), 0.005f, palette.pencil, unit)
    }
}

/** 휴 하고 안도하는 동안 머리 옆으로 땀방울이 흘러 떨어지고, 반대쪽으로 입김이 물결치며 흩어진다. */
private fun DrawScope.drawRelief(
    unit: Float,
    palette: StagePalette,
    actMillis: Int,
    pose: MascotPose,
) {
    val elapsed = actMillis - cueStart(RealtimeImageCue.EXHALE)
    val top = pose.y - MASCOT_HEIGHT * pose.scaleY
    val halfWidth = MASCOT_HALF_WIDTH * pose.scaleX
    if (elapsed in 41 until 760) {
        val appear = easeOutBack(((elapsed - 40) / 160f).coerceIn(0f, 1f))
        val slide = 0.02f * smooth((elapsed - 120) / 380f)
        val fall = (elapsed - 520).coerceAtLeast(0) / 240f
        val drop = 0.012f * appear * (1f - 0.5f * fall) * unit
        val x = (pose.x + halfWidth + 0.014f + 0.01f * fall) * unit
        val y = (top + 0.035f + slide + 0.06f * fall * fall) * unit
        if (drop > 0f) {
            drawPath(
                Path().apply {
                    moveTo(x, y - drop * 2.3f)
                    quadraticTo(x + drop * 1.1f, y - drop * 0.6f, x + drop, y)
                    arcTo(Rect(Offset(x, y), drop), 0f, 180f, false)
                    quadraticTo(x - drop * 1.1f, y - drop * 0.6f, x, y - drop * 2.3f)
                },
                mix(palette.paper, palette.pencil, 0.35f),
            )
        }
    }
    val puff = (elapsed - 180) / 700f
    if (puff <= 0f || puff >= 1f) return
    val x = pose.x - halfWidth - 0.012f - 0.05f * smooth(puff)
    val y = pose.y - MASCOT_HEIGHT * 0.42f - 0.012f * puff
    val width = 0.04f * (1f - 0.4f * puff)
    val breath = palette.ink.copy(alpha = sin(PI.toFloat() * puff))
    drawPath(
        Path().apply {
            moveTo(x * unit, y * unit)
            cubicTo(
                (x - width * 0.25f) * unit,
                (y - 0.012f) * unit,
                (x - width * 0.5f) * unit,
                (y + 0.012f) * unit,
                (x - width * 0.75f) * unit,
                y * unit,
            )
            quadraticTo((x - width * 0.9f) * unit, (y - 0.008f) * unit, (x - width) * unit, (y - 0.002f) * unit)
        },
        breath,
        style = Stroke(width = 0.006f * unit, cap = StrokeCap.Round),
    )
    drawCircle(breath, 0.004f * unit, Offset(x - width * 1.2f, y - 0.016f) * unit)
    drawCircle(breath, 0.0028f * unit, Offset(x - width * 1.45f, y - 0.03f) * unit)
}

/** 생각 점의 자리(머리 꼭대기에서), 반지름, 떠오르는 시점(고민 동작의 비율). */
private val ThoughtDots =
    listOf(
        Triple(Offset(0.06f, -0.016f), 0.006f, 0.2f),
        Triple(Offset(0.082f, -0.046f), 0.008f, 0.45f),
        Triple(Offset(0.108f, -0.086f), 0.011f, 0.7f),
    )

/**
 * 도화지를 올려다보며 고민하는 동안 머리 위로 생각 점이 하나씩 떠오르고, 번뜩이는 순간 점이 사라지며 느낌표가
 * 튀어나온다.
 */
private fun DrawScope.drawDaydream(
    unit: Float,
    palette: StagePalette,
    actMillis: Int,
) {
    val head = Offset(Home.x, FLOOR - MASCOT_HEIGHT)
    val idea = actMillis - cueStart(RealtimeImageCue.IDEA)
    val vanish = 1f - (idea / 150f).coerceIn(0f, 1f)
    val ponder = actMillis - cueStart(RealtimeImageCue.PONDER)
    for ((offset, radius, at) in ThoughtDots) {
        val pop = easeOutBack(((ponder - at * cueMillis(RealtimeImageCue.PONDER)) / 220f).coerceIn(0f, 1f))
        val dot = radius * pop * vanish
        if (dot > 0f) drawCircle(palette.ink, dot * unit, (head + offset) * unit)
    }
    if (idea < 0) return
    val size = easeOutBack((idea / 220f).coerceIn(0f, 1f)) * (1f - smooth((idea - 400) / 200f))
    if (size <= 0f) return
    val mark = head + Offset(0.1f, -0.1f)
    stageLine(mark - Offset(0f, 0.032f * size), mark + Offset(0f, 0.004f * size), 0.013f * size, palette.pencil, unit)
    drawCircle(palette.pencil, 0.0075f * size * unit, (mark + Offset(0f, 0.022f * size)) * unit)
    for (degrees in listOf(-50f, 0f, 50f)) {
        val angle = Math.toRadians((degrees - 90f).toDouble()).toFloat()
        val direction = Offset(cos(angle), sin(angle))
        val base = mark - Offset(0f, 0.008f)
        stageLine(base + direction * (0.045f * size), base + direction * (0.061f * size), 0.006f, palette.pencil, unit)
    }
}
