// 동작 곡선의 계수는 한 번씩만 쓰이는 연출 수치라 이름을 붙이면 오히려 움직임이 읽히지 않는다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.Offset
import app.manyak.designsystem.component.MASCOT_CENTER_TO_FEET_RATIO
import app.manyak.designsystem.component.MASCOT_HEIGHT_RATIO
import app.manyak.designsystem.component.MASCOT_WIDTH_RATIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/*
 * 마스코트 안무의 낱동작. 좌표는 표지 폭을 1 로 둔 값이다(3:4 라 높이는 4/3). 낱동작은 0..1 진행도를 자세로
 * 바꾸는 함수일 뿐이라, 어떤 순서로 이을지는 [completingMoment] 의 막이 정한다.
 */

internal const val STAGE_HEIGHT = 4f / 3f
internal const val MASCOT_SIZE = 0.24f
internal const val FLOOR = STAGE_HEIGHT - 0.11f
internal const val MASCOT_HALF_WIDTH = MASCOT_SIZE * MASCOT_WIDTH_RATIO / 2f
internal const val MASCOT_HEIGHT = MASCOT_SIZE * MASCOT_HEIGHT_RATIO
internal const val WALL_GAP = 0.05f

/** 마스코트 한 프레임의 자세. [x]·[y] 는 발끝 가운데다. */
internal data class MascotPose(
    val x: Float,
    val y: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val look: Offset = Offset.Zero,
    /** 0 은 뜬 눈, 1 은 지그시 감은 눈. */
    val squint: Float = 0f,
)

/** 낱동작 하나. [cue] 를 단 동작은 소품이 그 시작 시각과 진행도를 찾아 맞춰 움직인다. */
internal class Move(
    val millis: Int,
    val cue: Cue? = null,
    val pose: (Float) -> MascotPose,
)

internal fun Move.cue(cue: Cue) = Move(millis, cue, pose)

/** 제자리에서 숨 쉬듯 살짝 오르내린다. */
internal fun stand(
    millis: Int,
    at: Offset,
    look: Offset = Offset.Zero,
) = Move(millis) { f ->
    MascotPose(at.x, at.y, scaleY = 1f + 0.015f * sin(2f * PI.toFloat() * f), look = look)
}

/**
 * 가만히 서서 천천히 숨 쉬며 [glances] 쪽을 차례로 둘러본다. 한 곳을 한참 보다가 다음 곳으로 눈을 옮기고,
 * 끝에는 다시 앞을 본다.
 */
internal fun idle(
    millis: Int,
    at: Offset,
    glances: List<Offset> = listOf(Offset(-0.8f, -0.2f), Offset(0.8f, -0.2f)),
) = Move(millis) { f ->
    val keys = listOf(Offset.Zero) + glances + Offset.Zero
    val position = f * (keys.size - 1)
    val index = position.toInt().coerceAtMost(keys.size - 2)
    val shift = ((position - index - 0.6f) / 0.4f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val breath = sin(2f * PI.toFloat() * f * (millis / 1600f).roundToInt().coerceAtLeast(1))
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f - 0.01f * breath,
        scaleY = 1f + 0.02f * breath,
        look = keys[index] + (keys[index + 1] - keys[index]) * shift,
    )
}

/**
 * 작은 걸음으로 통통 튀며 천천히 걸어간다. 걸음마다 바닥을 살짝 누르고, 공중에서는 좌우로 번갈아 기운다.
 * 첫 걸음 전과 마지막 걸음 뒤는 바닥에 선 그대로라 앞뒤 동작과 이어진다.
 */
internal fun stroll(
    millis: Int,
    from: Offset,
    to: Offset,
    hops: Int,
) = Move(millis) { f ->
    val step = f * hops
    val index = step.toInt().coerceAtMost(hops - 1)
    val phase = step - index
    val contact = 0.28f
    val squash = if (phase < contact) sin(PI.toFloat() * phase / contact) else 0f
    val air = if (phase < contact) 0f else (phase - contact) / (1f - contact)
    val sway = if (index % 2 == 0) 1f else -1f
    MascotPose(
        x = from.x + (to.x - from.x) * f,
        y = from.y - 4f * air * (1f - air) * 0.035f,
        scaleX = 1f + 0.08f * squash,
        scaleY = 1f - 0.1f * squash + 0.03f * sin(PI.toFloat() * air),
        rotation = sway * 5f * sin(PI.toFloat() * air),
        look = Offset(sign(to.x - from.x) * 0.7f, 0f),
        squint = 0.13f * squash,
    )
}

/** 기지개를 켠다. 위로 쭉 늘어나며 눈을 지그시 감았다가 돌아온다. */
internal fun stretch(
    millis: Int,
    at: Offset,
) = Move(millis) { f ->
    val reach = sin(PI.toFloat() * f).let { it * it }
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f - 0.1f * reach,
        scaleY = 1f + 0.14f * reach,
        rotation = 5f * sin(2f * PI.toFloat() * f) * reach,
        squint = reach,
    )
}

/** 바닥을 누르듯 옆으로 퍼진다. 착지와 도약 준비를 같이 맡는다. */
internal fun crouch(
    millis: Int,
    at: Offset,
    depth: Float = 1f,
    look: Offset = Offset.Zero,
) = Move(millis) { f ->
    val squash = sin(PI.toFloat() * f) * depth
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f + 0.16f * squash,
        scaleY = 1f - 0.2f * squash,
        look = look,
        squint = 0.33f * squash,
    )
}

/**
 * [from] 에서 [to] 까지 포물선으로 난다. 시간에 고르게 나눈 2차 베지어가 곧 포물선이라 따로 중력을 풀지
 * 않는다. 빠르게 오르내릴수록 길게 늘어나고, [spins] 바퀴만큼 공중제비를 돈다.
 */
internal fun leap(
    millis: Int,
    from: Offset,
    to: Offset,
    lift: Float,
    spins: Float = 0f,
) = Move(millis) { f ->
    val control = Offset((from.x + to.x) / 2f, min(from.y, to.y) - 2f * lift)
    val position = from * ((1 - f) * (1 - f)) + control * (2 * f * (1 - f)) + to * (f * f)
    val velocity = (control - from) * (2 * (1 - f)) + (to - control) * (2 * f)
    val fastest = maxOf(abs(control.y - from.y), abs(to.y - control.y)) * 2f
    val stretch = if (fastest > 0f) 0.08f * abs(velocity.y) / fastest else 0f
    val turn = f * f * (3f - 2f * f)
    MascotPose(
        x = position.x,
        y = position.y,
        scaleX = 1f - stretch * 0.6f,
        scaleY = 1f + stretch,
        rotation = spins * 360f * turn,
        look = Offset(sign(to.x - from.x) * 0.8f, sign(velocity.y) * 0.6f),
    )
}

/**
 * 벽에 철썩 붙어 미끄러지다가 다시 웅크려 튀어 나간다. 벽 쪽 가장자리가 벽에 닿도록 찌그러진 폭만큼
 * 발끝을 옮긴다.
 */
internal fun cling(
    millis: Int,
    onLeft: Boolean,
    from: Float,
    to: Float,
) = Move(millis) { f ->
    val impact = (1f - f / 0.4f).coerceAtLeast(0f).let { it * it }
    val windUp = if (f > 0.65f) sin(PI.toFloat() * (f - 0.65f) / 0.35f) * 0.8f else 0f
    val squeeze = maxOf(impact, windUp)
    val scaleX = 1f - 0.22f * squeeze
    val halfWidth = MASCOT_HALF_WIDTH * scaleX
    val away = if (onLeft) 1f else -1f
    MascotPose(
        x = if (onLeft) WALL_GAP + halfWidth else 1f - WALL_GAP - halfWidth,
        y = from + (to - from) * f * f,
        scaleX = scaleX,
        scaleY = 1f + 0.12f * squeeze,
        rotation = -away * 6f * windUp,
        look = Offset(away * 0.9f, -0.3f),
        squint = 0.27f * impact,
    )
}

/**
 * 몸 가운데를 축으로 도는 마스코트를 발을 축으로 돈 것처럼 보이게 놓는다. 몸 가운데는 발에서 [MASCOT_SIZE]
 * 의 일정 비율만큼 위에 있어, 돈 만큼 발이 밀려나는 거리를 미리 되돌린다.
 */
internal fun pinnedAtFeet(
    feet: Offset,
    rotation: Float,
): MascotPose {
    val radians = Math.toRadians(rotation.toDouble()).toFloat()
    val toCenter = MASCOT_SIZE * MASCOT_CENTER_TO_FEET_RATIO
    return MascotPose(
        x = feet.x + toCenter * sin(radians),
        y = feet.y + toCenter * (1f - cos(radians)),
        rotation = rotation,
    )
}

/**
 * 붓 끝인 발로 [curve] 를 따라 긋는다. 곡선의 기울기만큼 몸을 기울이되 발을 축으로 돌려 붓 끝이 선 위에
 * 남고, 기울기는 양 끝에서 0 으로 모아 앞뒤 도약과 이어 붙인다.
 */
internal fun brush(
    millis: Int,
    curve: Cubic,
) = Move(millis) { f ->
    val tangent = curve.tangent(f)
    val slope = Math.toDegrees(atan2(tangent.y, abs(tangent.x)).toDouble()).toFloat()
    val envelope = minOf(1f, f / 0.12f, (1f - f) / 0.12f)
    val rotation = (-slope * 0.45f).coerceIn(-20f, 20f) * sign(tangent.x) * envelope
    pinnedAtFeet(curve.at(f), rotation).copy(look = Offset(sign(tangent.x) * 0.8f, 0.5f))
}

/** 3차 베지어 곡선. 붓질의 길과 그 길을 그리는 선이 같은 값을 쓴다. */
internal class Cubic(
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset,
) {
    fun at(t: Float): Offset {
        val u = 1f - t
        return start * (u * u * u) + control1 * (3 * u * u * t) + control2 * (3 * u * t * t) + end * (t * t * t)
    }

    fun tangent(t: Float): Offset {
        val u = 1f - t
        return (control1 - start) * (3 * u * u) + (control2 - control1) * (6 * u * t) + (end - control2) * (3 * t * t)
    }
}
