// 소품의 자리와 크기는 표지 폭이나 심벌 viewport 에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.manyak.designsystem.mascot.BRUSH_TILT
import app.manyak.designsystem.mascot.BrushGrip
import app.manyak.designsystem.mascot.HAT_TILT
import app.manyak.designsystem.mascot.HatAnchor
import app.manyak.designsystem.mascot.MascotEyes
import app.manyak.designsystem.mascot.MascotPose
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.drawBeret
import app.manyak.designsystem.mascot.drawBrush
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.inViewport
import app.manyak.designsystem.mascot.mix
import app.manyak.designsystem.mascot.onBody
import app.manyak.designsystem.mascot.smooth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 베레모와 붓. 막 처음에 베레모가 흔들리며 떨어져 얹히고, 바닥의 붓이 점프에 맞춰 한 바퀴 돌며 손에 들어온다.
 * 그림을 마치고 뛰어오르는 순간에는 둘 다 졸업 모자처럼 하늘로 던져 화면 위로 사라진다.
 */
internal fun DrawScope.drawPainterGear(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
    pose: MascotPose,
    millis: Int,
) {
    val sinceToss = actMillis - cueStart(Cue.TOSS)
    val facing = actMillis - cueStart(Cue.FACE)
    val paint = if (facing in 0 until cueMillis(Cue.FACE)) palette.pencil else palette.brand
    if (sinceToss >= 0) {
        // 던지는 순간의 자리에서 출발해 마스코트보다 빠르게 솟구치며 돈다.
        val tossed = sinceToss / 1000f
        val from = completingMoment(millis - sinceToss).pose
        val hat = from.onBody(HatAnchor, MASCOT_SIZE)
        val grip = from.onBody(BrushGrip, MASCOT_SIZE)
        val hatAt = Offset(hat.x - 0.25f * tossed, hat.y - 2.2f * tossed + tossed * tossed)
        inViewport(unit, MASCOT_SIZE, hatAt, HAT_TILT - 540f * tossed, 1f) { drawBeret(palette) }
        val brushAt = Offset(grip.x + 0.3f * tossed, grip.y - 2.6f * tossed + 1.2f * tossed * tossed)
        inViewport(unit, MASCOT_SIZE, brushAt, BRUSH_TILT - 720f * tossed, 1f) { drawBrush(palette, paint) }
        return
    }

    val fall = cueProgress(Cue.HAT, actMillis)
    val hat = pose.onBody(HatAnchor, MASCOT_SIZE)
    inViewport(
        unit,
        MASCOT_SIZE,
        Offset(hat.x, hat.y - 1.2f * (1f - fall * fall)),
        HAT_TILT + pose.rotation + 22f * sin(fall * PI.toFloat() * 2.5f) * (1f - fall),
        pose.scaleX,
    ) { drawBeret(palette) }

    val held = pose.onBody(BrushGrip, MASCOT_SIZE)
    val heldAngle = BRUSH_TILT + pose.rotation
    val catchMillis = cueMillis(Cue.BRUSH) * BRUSH_CATCH
    val flight = ((actMillis - cueStart(Cue.BRUSH)) / catchMillis).coerceIn(0f, 1f)
    if (flight >= 1f) {
        inViewport(unit, MASCOT_SIZE, held, heldAngle, 1f) { drawBrush(palette, paint) }
        return
    }
    val grip = BrushOnFloor + (held - BrushOnFloor) * flight - Offset(0f, 0.12f * sin(PI.toFloat() * flight))
    inViewport(unit, MASCOT_SIZE, grip, -450f + (heldAngle + 450f) * flight, 1f) {
        drawBrush(palette, mix(palette.paper, palette.pencil, 0.25f))
    }
}

/**
 * 에너지 드링크 캔. 위에서 돌며 떨어져 점프한 손에 들어오고, 얼굴 앞으로 들어 기울여 세 모금 마신 뒤, 힘이
 * 차오르는 순간 빙글 돌며 화면 밖으로 날아간다. 힘이 차오르는 동안 몸 둘레로 번개가 차례로 튄다.
 */
internal fun DrawScope.drawEnergyDrink(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
    pose: MascotPose,
) {
    val dropStart = cueStart(Cue.CAN_DROP)
    val catchAt = cueStart(Cue.CAN_CATCH) + cueMillis(Cue.CAN_CATCH) * CAN_CATCH
    val powerStart = cueStart(Cue.POWER_UP)
    if (actMillis < dropStart) return
    val (at, angle) =
        when {
            actMillis < catchAt -> {
                val fall = (actMillis - dropStart) / (catchAt - dropStart)
                val held = pose.onBody(CanGrip, MASCOT_SIZE)
                Offset(
                    CanDropFrom.x + (held.x - CanDropFrom.x) * fall,
                    CanDropFrom.y + (held.y - CanDropFrom.y) * fall * fall,
                ) to 200f * (1f - fall) + pose.rotation * fall
            }
            actMillis < powerStart -> {
                val sip = cueProgress(Cue.DRINK, actMillis)
                val raise = if (actMillis < cueStart(Cue.DRINK)) 0f else smooth(sip / 0.2f)
                pose.onBody(CanGrip + (CanSip - CanGrip) * raise, MASCOT_SIZE) to
                    CAN_SIP_TILT * raise + 8f * gulpAt(sip) + pose.rotation
            }
            else -> {
                val flown = (actMillis - powerStart) / 1000f
                val from = pose.onBody(CanSip, MASCOT_SIZE)
                drawBolts(cueProgress(Cue.POWER_UP, actMillis), pose, actMillis, unit, palette)
                Offset(from.x + 0.9f * flown, from.y - 1.8f * flown + 0.8f * flown * flown) to
                    CAN_SIP_TILT + 900f * flown
            }
        }
    if (at.x > 1.2f || at.y < -0.2f) return
    inViewport(unit, MASCOT_SIZE, at, angle, 1f) { drawCan(palette) }
}

/** 번개 마크 캔. 가운데를 원점으로 심벌 viewport 단위로 그린다. */
private fun DrawScope.drawCan(palette: StagePalette) {
    val body = Rect(-7.5f, -12f, 7.5f, 12f)
    drawRoundRect(palette.brand, body.topLeft, body.size, CornerRadius(3f))
    drawRoundRect(palette.ink, body.topLeft, body.size, CornerRadius(3f), style = Stroke(width = 0.9f))
    drawLine(mix(palette.paper, palette.pencil, 0.35f), Offset(-6f, -12.5f), Offset(6f, -12.5f), 2.6f, StrokeCap.Round)
    drawPath(
        polygon(1.8f, -8f, -3.6f, 1.2f, -0.2f, 1.2f, -1.8f, 8f, 3.8f, -1.6f, 0.3f, -1.6f),
        palette.paper,
    )
}

private val BoltAngles = listOf(-70f, -32f, 30f, 68f, 112f, -112f)
private val Bolt = polygon(0.1f, -1f, -0.4f, 0.08f, 0.02f, 0.08f, -0.15f, 1f, 0.45f, -0.18f, 0.05f, -0.18f)

/** 힘이 차오르는 동안 몸 둘레로 번개가 차례로 튀어나와 깜빡이다가 사라진다. */
private fun DrawScope.drawBolts(
    progress: Float,
    pose: MascotPose,
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val center = Offset(pose.x, pose.y - MASCOT_HEIGHT * pose.scaleY * 0.55f)
    BoltAngles.forEachIndexed { index, degrees ->
        val pop = easeOutBack(((progress - index * 0.07f) / 0.15f).coerceIn(0f, 1f))
        val fade = 1f - ((progress - 0.82f) / 0.18f).coerceIn(0f, 1f)
        val flicker = if ((actMillis / 70 + index) % 3 == 0) 0.75f else 1f
        val size = 0.055f * pop * fade * flicker
        if (size <= 0f) return@forEachIndexed
        val angle = Math.toRadians(degrees.toDouble()).toFloat()
        val at = center + Offset(sin(angle), -cos(angle)) * 0.15f
        translate(at.x * unit, at.y * unit) {
            rotate(degrees, pivot = Offset.Zero) {
                scale(size * unit, pivot = Offset.Zero) { drawPath(Bolt, palette.brand) }
            }
        }
    }
}

/**
 * 표정을 거드는 소품. 카드를 올려다볼 때 머리 위 물음표, 꾸벅 졸 때 피어오르는 Z, 어지러울 때 머리 위를 도는
 * 별을 그린다.
 */
internal fun DrawScope.drawMoodProps(
    act: CompletingAct,
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
    pose: MascotPose,
    millis: Int,
) {
    val top = pose.y - MASCOT_HEIGHT * pose.scaleY
    if (act == CompletingAct.STORYLINE) {
        for (cue in listOf(Cue.STORYLINE_1_LOOK, Cue.STORYLINE_3_LOOK)) {
            val shown = sin(PI.toFloat() * cueProgress(cue, actMillis))
            if (shown > 0f) {
                val size = 0.04f * easeOutBack(minOf(shown * 1.6f, 1f))
                drawQuestion(Offset(pose.x + 0.075f, top - 0.06f), size, palette.pencil, unit)
            }
        }
    }
    if (act == CompletingAct.ENERGY) drawSnores(actMillis - cueStart(Cue.DOZE), pose.x, top, unit, palette.pencil)
    if (pose.eyes == MascotEyes.DIZZY) {
        for (index in 0 until 3) {
            val angle = millis / 160f + index * 2f * PI.toFloat() / 3f
            val at = Offset(pose.x + cos(angle) * 0.075f, top - 0.03f + sin(angle) * 0.022f)
            drawSparkle(at * unit, 0.02f * unit, palette.brand)
        }
    }
}

/** 꾸벅 조는 동안 머리 옆에서 Z 가 차례로 피어올라 커지며 흐려진다. */
private fun DrawScope.drawSnores(
    dozing: Int,
    x: Float,
    top: Float,
    unit: Float,
    color: Color,
) {
    if (dozing > cueMillis(Cue.DOZE)) return
    for (index in 0 until 3) {
        val life = (dozing - index * 300) / 650f
        if (life <= 0f || life >= 1f) continue
        val size = (0.018f + 0.016f * life) * unit
        val at = Offset(x + 0.08f + 0.05f * life, top - 0.02f - 0.12f * life) * unit
        drawPath(
            Path().apply {
                moveTo(at.x - size, at.y - size)
                lineTo(at.x + size, at.y - size)
                lineTo(at.x - size, at.y + size)
                lineTo(at.x + size, at.y + size)
            },
            color.copy(alpha = sin(PI.toFloat() * life)),
            style = Stroke(width = 0.009f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 물음표. 둥근 고리와 짧은 기둥, 아래 점으로 이룬다. [size] 는 표지 폭 단위다. */
private fun DrawScope.drawQuestion(
    at: Offset,
    size: Float,
    color: Color,
    unit: Float,
) {
    if (size <= 0f) return
    translate(at.x * unit, at.y * unit) {
        scale(size * unit, pivot = Offset.Zero) {
            drawPath(
                Path().apply {
                    arcTo(Rect(Offset(0f, -0.45f), 0.45f), 198f, 225f, forceMoveTo = true)
                    quadraticTo(0f, -0.05f, 0f, 0.25f)
                },
                color,
                style = Stroke(width = 0.3f, cap = StrokeCap.Round),
            )
            drawCircle(color, 0.17f, Offset(0f, 0.72f))
        }
    }
}

/** 꼭짓점 좌표(x, y 차례)로 닫힌 다각형을 만든다. */
private fun polygon(vararg xy: Float) =
    Path().apply {
        moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
        close()
    }
