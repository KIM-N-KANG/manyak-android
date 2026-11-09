// 눈 모양의 좌표는 심벌 viewport 안에서 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import app.manyak.designsystem.mascot.MascotEyes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 로고 심벌을 움직이는 캐릭터로 그린다. 눈이 따로 움직여야 해서 drawable 대신 ic_manyak_symbol 과 같은
 * 경로를 직접 그린다.
 *
 * [feet] 는 심벌 아래 끝의 가운데다. 찌그러뜨림([scaleX]·[scaleY])은 이 점을 붙잡아 바닥에 선 채로 퍼지고,
 * 회전은 몸 가운데를 축으로 돈다. [size] 는 64 단위 viewport 한 변의 픽셀이다.
 *
 * @param look 눈이 보는 방향. 각 축 -1..1 이 viewport 2 단위만큼 눈을 옮긴다.
 * @param eyeOpenness 1 은 뜬 눈, 0 에 가까울수록 감은 눈이다. 동그란 눈에만 쓴다.
 * @param eyes 눈 모양. 윙크는 왼눈만 뜨고 오른눈은 웃으며 감는다.
 */
fun DrawScope.drawManyakMascot(
    feet: Offset,
    size: Float,
    color: Color,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    rotationDegrees: Float = 0f,
    look: Offset = Offset.Zero,
    eyeOpenness: Float = 1f,
    eyes: MascotEyes = MascotEyes.ROUND,
) {
    val unit = size / MASCOT_VIEWPORT
    translate(left = feet.x - MASCOT_CENTER * unit, top = feet.y - MASCOT_BOTTOM * unit) {
        rotate(rotationDegrees, pivot = Offset(MASCOT_CENTER * unit, MASCOT_CENTER * unit)) {
            scale(scaleX, scaleY, pivot = Offset(MASCOT_CENTER * unit, MASCOT_BOTTOM * unit)) {
                scale(unit, pivot = Offset.Zero) {
                    drawPath(
                        path = MascotBody,
                        color = color,
                        style = Stroke(width = BODY_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    for ((eyeX, side) in EyeXs.zip(listOf(-1f, 1f))) {
                        val shape =
                            when {
                                eyes != MascotEyes.WINK -> eyes
                                side < 0f -> MascotEyes.ROUND
                                else -> MascotEyes.SMILE
                            }
                        drawEye(shape, eyeX, side, color, look, eyeOpenness)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawEye(
    shape: MascotEyes,
    eyeX: Float,
    side: Float,
    color: Color,
    look: Offset,
    openness: Float,
) {
    val line = Stroke(width = EYE_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (shape) {
        MascotEyes.ROUND -> {
            val eyeHeight = EYE_RADIUS * 2f * openness.coerceIn(EYE_MIN_OPENNESS, 1f)
            drawOval(
                color = color,
                topLeft = Offset(eyeX - EYE_RADIUS + look.x * EYE_TRAVEL, EYE_Y - eyeHeight / 2f + look.y * EYE_TRAVEL),
                size = Size(EYE_RADIUS * 2f, eyeHeight),
            )
        }
        MascotEyes.FOCUS ->
            drawPath(
                Path().apply {
                    moveTo(eyeX + side * 4f, 20f)
                    lineTo(eyeX - side * 3f, EYE_Y)
                    lineTo(eyeX + side * 4f, 28f)
                },
                color,
                style = line,
            )
        MascotEyes.SMILE, MascotEyes.SLEEPY -> {
            val bend = if (shape == MascotEyes.SMILE) -7f else 5f
            drawPath(
                Path().apply {
                    moveTo(eyeX - 4.5f, 25f - bend / 3f)
                    quadraticTo(eyeX, 25f + bend, eyeX + 4.5f, 25f - bend / 3f)
                },
                color,
                style = line,
            )
        }
        MascotEyes.SPARKLE -> drawPath(starPath(eyeX), color)
        MascotEyes.DIZZY ->
            drawPath(
                spiralPath(eyeX, side),
                color,
                style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        MascotEyes.WINK -> Unit
    }
}

/** 반짝이는 별 눈. 긴 꼭짓점과 짧은 꼭짓점이 번갈아 도는 네 갈래 별이다. */
private fun starPath(eyeX: Float) =
    Path().apply {
        for (corner in 0 until 8) {
            val radius = if (corner % 2 == 0) 6f else 1.7f
            val angle = corner * PI.toFloat() / 4f
            val x = eyeX + sin(angle) * radius
            val y = EYE_Y - cos(angle) * radius
            if (corner == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** 어지러운 눈. 가운데에서 두 바퀴 감겨 나오는 소용돌이로, 양쪽 눈이 서로 반대로 돈다. */
private fun spiralPath(
    eyeX: Float,
    side: Float,
) = Path().apply {
    moveTo(eyeX + 0.5f, EYE_Y)
    for (step in 1..24) {
        val t = step / 24f
        val angle = t * PI.toFloat() * 4f * side
        lineTo(eyeX + cos(angle) * (0.5f + 4.3f * t), EYE_Y + sin(angle) * (0.5f + 4.3f * t))
    }
}

/** 심벌이 차지하는 폭과 높이(viewport 한 변에 대한 비율). 벽·천장에 닿는 계산에 쓴다. */
const val MASCOT_WIDTH_RATIO = (55.8f - 8.2f) / 64f
const val MASCOT_HEIGHT_RATIO = (59.8f - 4.2f) / 64f

/** 회전축(몸 가운데)이 발에서 위로 떨어진 거리. 발을 축으로 돌린 것처럼 보정할 때 쓴다. */
const val MASCOT_CENTER_TO_FEET_RATIO = (59.8f - 32f) / 64f

private val MascotBody: Path by lazy {
    PathParser()
        .parsePathString("M30,44H24A12,12 0 0 1 12,32V20A12,12 0 0 1 24,8H40A12,12 0 0 1 52,20V44C52,51 50,56 44,56H16")
        .toPath()
}

internal const val MASCOT_VIEWPORT = 64f
internal const val MASCOT_CENTER = 32f

/** 심벌 선의 아래 끝(56 + 선 두께의 절반). */
internal const val MASCOT_BOTTOM = 59.8f
private const val BODY_STROKE = 7.6f
private const val EYE_Y = 24f
private const val EYE_RADIUS = 4f
private const val EYE_STROKE = 3.4f
private const val EYE_TRAVEL = 2f
private const val EYE_MIN_OPENNESS = 0.1f
private val EyeXs = listOf(25f, 39f)
