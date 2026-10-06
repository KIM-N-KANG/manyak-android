// 소품의 자리와 크기는 무대 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber", "TooManyFunctions")

package app.manyak.chat.room.presentation.scene

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.math.exp
import kotlin.math.sin

/**
 * 실시간 이미지를 기다리는 4:3 장면 자리. 마스코트가 화가 베레모를 쓰고 붓으로 인물 세 장을 그려 빨랫줄에
 * 거는 막을 연기한다. 안무는 [realtimeImageMoment] 가 정하고 여기서는 그리기만 한다.
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

/** 그림이 도화지에서 빨랫줄까지 날아가는 시간. */
private const val FLIGHT_MILLIS = 600

/** 새 도화지가 이젤에 튀어나오는 시간. */
private const val POP_MILLIS = 320
private const val RESET_FADE_START = 600
private const val RESET_FADE_MILLIS = 400f
private const val HUNG_SCALE = HUNG_WIDTH / PAPER_WIDTH
private val PaperTopCenter = Offset((PAPER_LEFT + PAPER_RIGHT) / 2f, PAPER_TOP)
private val PaperBottomCenter = Offset(PaperTopCenter.x, PAPER_BOTTOM)

/** 그림이 빨랫줄에 걸리는 한 바퀴 안의 시각. 날아가기 시작하는 시각은 [FLIGHT_MILLIS] 앞이다. */
private val HangAt =
    listOf(
        actStart(RealtimeImageAct.RELIEF) + cueStart(RealtimeImageCue.EXHALE) + 100 + FLIGHT_MILLIS,
        actStart(RealtimeImageAct.DAYDREAM) + FLIGHT_MILLIS,
        actStart(RealtimeImageAct.RESET) + FLIGHT_MILLIS,
    )
private val Pictures = Picture.entries

/** 바탕 점, 빨랫줄과 걸린 그림, 이젤 도화지, 마스코트, 막에 맞는 소품, 날아가는 그림을 차례로 얹는다. */
private fun DrawScope.drawScene(
    millis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val (act, actMillis, pose) = realtimeImageMoment(millis)
    val loopMillis = loopTime(millis)
    val reveal = Reveal({ cue -> (actMillis - cueStart(cue)).toFloat() }, millis)
    drawGallery(unit, palette, loopMillis)
    drawEasel(unit, palette)
    drawEaselPaper(unit, palette, act, actMillis, loopMillis, reveal)
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
    when (act) {
        RealtimeImageAct.HYPE -> drawEffort(unit, palette, actMillis, pose)
        RealtimeImageAct.SKETCH -> drawSpeedLines(unit, palette, actMillis, pose)
        RealtimeImageAct.RELIEF -> drawRelief(unit, palette, actMillis, pose)
        RealtimeImageAct.DAYDREAM -> drawDaydream(unit, palette, actMillis)
        else -> Unit
    }
    drawFlights(unit, palette, loopMillis)
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

/** 이젤 다리와 받침. 도화지가 날아가도 이젤은 남는다. */
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
 * 이젤에 놓인 도화지. 막에 따라 빈 종이, 그리는 중인 그림, 뒤집히는 종이를 보이고, 그림이 빨랫줄로 날아간
 * 뒤에는 새 종이가 바닥 쪽을 붙잡고 통 튀어나온다.
 */
private fun DrawScope.drawEaselPaper(
    unit: Float,
    palette: StagePalette,
    act: RealtimeImageAct,
    actMillis: Int,
    loopMillis: Int,
    reveal: Reveal,
) {
    val hanging = HangAt.indexOfFirst { loopMillis >= it - FLIGHT_MILLIS && loopMillis < it + POP_MILLIS }
    if (hanging >= 0) {
        val pop = (loopMillis - HangAt[hanging]) / POP_MILLIS.toFloat()
        if (pop < 0f) return
        val grow = 0.85f + 0.15f * easeOutBack(pop.coerceIn(0f, 1f))
        withAlpha((pop * 2f).coerceIn(0f, 1f)) {
            onGrid(unit, PaperBottomCenter, Offset(PAPER_GRID / 2f, GRID_HEIGHT), grow, grow, 0f) {
                drawSheet(palette, null, Done)
            }
        }
        return
    }
    if (act == RealtimeImageAct.CONSTELLATION) {
        val flip = smooth((cueProgress(RealtimeImageCue.FLIP, actMillis) - 0.1f) / 0.55f)
        val back = flip >= 0.5f
        val lifted = PaperTopCenter - Offset(0f, 0.012f * sin(PI.toFloat() * flip))
        onGrid(unit, lifted, Offset(PAPER_GRID / 2f, 0f), abs(cos(PI.toFloat() * flip)).coerceAtLeast(0.02f), 1f, 0f) {
            drawSheet(palette, if (back) Picture.CONSTELLATION else null, reveal, night = back)
        }
        return
    }
    // 안도하는 막에서 크로키가 날아간 뒤로는 새 빈 종이다.
    val picture =
        when {
            act == RealtimeImageAct.SKETCH -> Picture.SKETCH
            act == RealtimeImageAct.RELIEF && loopMillis < HangAt[0] - FLIGHT_MILLIS -> Picture.SKETCH
            act == RealtimeImageAct.WATERCOLOR -> Picture.WATERCOLOR
            else -> null
        }
    onGrid(unit, Offset(PAPER_LEFT, PAPER_TOP), Offset.Zero, 1f, 1f, 0f) {
        drawSheet(palette, picture, if (act == RealtimeImageAct.RELIEF) Done else reveal)
    }
}

/** 빨랫줄과 거기 걸린 그림. 걸린 그림은 살짝 흔들리다 멈추고, 한 바퀴 끝에 함께 떨어지며 사라진다. */
private fun DrawScope.drawGallery(
    unit: Float,
    palette: StagePalette,
    loopMillis: Int,
) {
    val fade = ((loopMillis - actStart(RealtimeImageAct.RESET) - RESET_FADE_START) / RESET_FADE_MILLIS).coerceIn(0f, 1f)
    drawPath(
        Path().apply {
            moveTo(LINE_LEFT * unit, LINE_Y * unit)
            quadraticTo(
                (LINE_LEFT + LINE_RIGHT) / 2f * unit,
                (LINE_Y + 2f * LINE_SAG) * unit,
                LINE_RIGHT * unit,
                LINE_Y * unit,
            )
        },
        palette.ink,
        style = Stroke(width = 0.005f * unit),
    )
    HangAt.forEachIndexed { index, at ->
        val since = loopMillis - at
        if (since < 0) return@forEachIndexed
        val slot = HangSlots[index]
        val swing = 9f * exp(-since / 320f) * sin(since / 75f)
        val top = Offset(slot.x, clotheslineY(slot.x) - 0.006f + 0.04f * fade * fade)
        withAlpha(1f - fade) {
            onGrid(unit, top, Offset(PAPER_GRID / 2f, 0f), HUNG_SCALE, HUNG_SCALE, slot.tilt + swing) {
                drawSheet(palette, Pictures[index], Done)
                drawRoundRect(palette.ink, Offset(PAPER_GRID / 2f - 7f, -12f), Size(14f, 26f), CornerRadius(3f))
            }
        }
    }
}

/** 다 그린 그림이 이젤에서 빨랫줄로 포물선을 그리며 줄어들어 날아간다. */
private fun DrawScope.drawFlights(
    unit: Float,
    palette: StagePalette,
    loopMillis: Int,
) {
    HangAt.forEachIndexed { index, at ->
        val raw = (loopMillis - (at - FLIGHT_MILLIS)) / FLIGHT_MILLIS.toFloat()
        if (raw < 0f || raw >= 1f) return@forEachIndexed
        val f = smooth(raw)
        val slot = HangSlots[index]
        val to = Offset(slot.x, clotheslineY(slot.x) - 0.006f)
        val from = PaperTopCenter
        val control = Offset((from.x + to.x) / 2f, minOf(from.y, to.y) - 0.05f)
        val position = from * ((1 - f) * (1 - f)) + control * (2 * f * (1 - f)) + to * (f * f)
        val scale = 1f + (HUNG_SCALE - 1f) * f
        onGrid(unit, position, Offset(PAPER_GRID / 2f, 0f), scale, scale, slot.tilt * f - 14f * sin(PI.toFloat() * f)) {
            drawSheet(palette, Pictures[index], Done)
        }
    }
}

/** 막에 맞는 붓털 색. 처음엔 깨끗하고, 크로키와 별자리는 연필 회색, 수채는 옅은 초록이다. */
private fun paintColor(
    palette: StagePalette,
    act: RealtimeImageAct,
): Color =
    when (act) {
        RealtimeImageAct.GEAR_UP, RealtimeImageAct.HYPE -> mix(palette.paper, palette.pencil, 0.25f)
        RealtimeImageAct.WATERCOLOR, RealtimeImageAct.DAYDREAM -> mix(palette.paper, palette.brand, 0.4f)
        else -> palette.pencil
    }

/**
 * 화가 베레모와 붓. 준비 막에서 베레모는 하늘에서 흔들리며 떨어져 머리에 얹히고, 바닥에 누운 붓은 점프에 맞춰
 * 한 바퀴 돌며 손에 들어온다. 그 뒤로는 몸을 따라 함께 기울고 찌그러진다.
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
    val heldAngle = BRUSH_TILT + pose.rotation
    val catchMillis = cueMillis(RealtimeImageCue.BRUSH) * BRUSH_CATCH
    val flight = if (gearingUp) ((actMillis - cueStart(RealtimeImageCue.BRUSH)) / catchMillis).coerceIn(0f, 1f) else 1f
    val grip =
        if (flight >= 1f) {
            held
        } else {
            BrushOnFloor + (held - BrushOnFloor) * flight - Offset(0f, 0.08f * sin(PI.toFloat() * flight))
        }
    val angle = if (flight >= 1f) heldAngle else -450f + (heldAngle + 450f) * flight
    inViewport(unit, MASCOT_SIZE, grip, angle, 1f) { drawBrush(palette, paintColor(palette, act)) }
}

/** 소품을 몸에 붙여 그릴 때 쓰는 몸 가운데. */
private fun MascotPose.center() = Offset(x, y - MASCOT_HEIGHT * scaleY * 0.55f)

/** 힘을 모으는 동안 머리 둘레에 기합 선이 번갈아 깜빡이고, 펌프 점프마다 몸 둘레로 반짝임이 퍼진다. */
private fun DrawScope.drawEffort(
    unit: Float,
    palette: StagePalette,
    actMillis: Int,
    pose: MascotPose,
) {
    val center = pose.center()
    val charge = cueProgress(RealtimeImageCue.CHARGE, actMillis)
    if (charge > 0f && charge < 1f) {
        val grow = smooth(charge / 0.3f) * (1f - smooth((charge - 0.85f) / 0.15f))
        for (side in listOf(-1f, 1f)) {
            listOf(32f, 58f, 84f).forEachIndexed { index, degrees ->
                val angle = Math.toRadians(degrees.toDouble()).toFloat()
                val direction = Offset(side * sin(angle), -cos(angle))
                val pulse = 0.65f + 0.35f * sin(actMillis / 35f + index * 2f)
                val inner = 0.072f
                val outer = inner + 0.026f * grow * pulse
                if (outer - inner >= 0.002f) {
                    stageLine(center + direction * inner, center + direction * outer, 0.008f, palette.ink, unit)
                }
            }
        }
    }
    for (cue in listOf(RealtimeImageCue.PUMP_1, RealtimeImageCue.PUMP_2)) {
        val burst = (actMillis - cueStart(cue)) / 320f
        if (burst < 0f || burst >= 1f) continue
        for (ray in 0 until 6) {
            val angle = ray / 6f * 2f * PI.toFloat() + PI.toFloat() / 6f
            val direction = Offset(cos(angle), sin(angle))
            val from = center + direction * (0.075f + 0.05f * burst)
            stageLine(from, from + direction * (0.022f * (1f - burst)), 0.007f * (1f - burst * 0.6f), palette.ink, unit)
        }
    }
}

/** 연필로 휘갈기는 동안 마스코트 뒤로 짧은 속도 선이 따라붙는다. */
private fun DrawScope.drawSpeedLines(
    unit: Float,
    palette: StagePalette,
    actMillis: Int,
    pose: MascotPose,
) {
    for ((cue, stroke) in listOf(RealtimeImageCue.CONTOUR to SketchContour, RealtimeImageCue.HATCH to SketchHatch)) {
        val progress = cueProgress(cue, actMillis)
        if (progress <= 0.04f || progress >= 0.96f) continue
        val along = stroke.at(progress) - stroke.at(progress - 0.06f)
        val back = -along / (along.getDistance().takeIf { it > 0f } ?: 1f)
        val center = pose.center()
        for (side in listOf(-1f, 1f)) {
            val start = center + back * 0.07f + Offset(-back.y, back.x) * (side * 0.022f)
            stageLine(start, start + back * 0.03f, 0.006f, palette.ink, unit)
        }
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
 * 빨랫줄의 그림을 올려다보며 고민하는 동안 머리 위로 생각 점이 하나씩 떠오르고, 번뜩이는 순간 점이 사라지며
 * 느낌표가 튀어나온다.
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
