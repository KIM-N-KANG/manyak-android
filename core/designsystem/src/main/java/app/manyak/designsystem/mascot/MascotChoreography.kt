// 동작 곡선의 계수는 한 번씩만 쓰이는 연출 수치라 이름을 붙이면 오히려 움직임이 읽히지 않는다. 낱동작은
// 안무가 골라 쓰는 재료라 한 파일에 모아 둔다.
@file:Suppress("MagicNumber", "TooManyFunctions")

package app.manyak.designsystem.mascot

import androidx.compose.ui.geometry.Offset
import app.manyak.designsystem.component.MASCOT_BOTTOM
import app.manyak.designsystem.component.MASCOT_CENTER
import app.manyak.designsystem.component.MASCOT_CENTER_TO_FEET_RATIO
import app.manyak.designsystem.component.MASCOT_VIEWPORT
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/*
 * 로고 마스코트 안무의 공용 재료. 자세, 낱동작, 막을 이어 붙여 시간 → 자세를 계산하는 순수 함수를 둔다.
 * 제작 탭 완성 중 표지와 채팅 실시간 이미지 대기가 함께 쓰고, 웹과 같은 값을 쓴다.
 *
 * 좌표는 무대 폭을 1 로 둔 값이다. 마스코트 크기(size)는 무대마다 달라 크기에 기대는 동작만 인자로 받는다.
 */

/**
 * 눈 모양. FOCUS 는 힘주는 `> <`, SMILE 은 웃으며 감은 `^ ^`, SLEEPY 는 졸려 감긴 `‿ ‿`, SPARKLE 은 반짝이는
 * 별 눈, WINK 는 한쪽만 웃으며 감은 눈, DIZZY 는 빙글빙글 도는 어지러운 눈이다.
 */
enum class MascotEyes { ROUND, FOCUS, SMILE, SLEEPY, SPARKLE, WINK, DIZZY }

/** 마스코트 한 프레임의 자세. [x]·[y] 는 발끝 가운데다. */
data class MascotPose(
    val x: Float,
    val y: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val look: Offset = Offset.Zero,
    /** 0 은 뜬 눈, 1 은 지그시 감은 눈. */
    val squint: Float = 0f,
    val eyes: MascotEyes = MascotEyes.ROUND,
    /** 몸에 쥔 붓을 몸 기준으로 더 돌린 각도(도). 0 이면 [BRUSH_TILT] 그대로다. */
    val brush: Float = 0f,
    /** 붓털을 누른 정도. 0 은 뾰족한 붓털, 1 은 눌려 넓게 퍼진 붓털이다. */
    val press: Float = 0f,
)

/** 낱동작 하나. [cue] 를 단 동작은 소품이 그 시작 시각과 진행도를 찾아 맞춰 움직인다. */
class Move<out C>(
    val millis: Int,
    val cue: C? = null,
    val pose: (Float) -> MascotPose,
)

fun <C> Move<*>.cue(cue: C): Move<C> = Move(millis, cue, pose)

/** 동작 내내 [eyes] 눈 모양을 쓴다. */
fun <C> Move<C>.eyes(eyes: MascotEyes): Move<C> = Move(millis, cue) { f -> pose(f).copy(eyes = eyes) }

/** 0..1 을 부드럽게 시작하고 멈추는 곡선으로 바꾼다. */
fun smooth(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** 살짝 넘쳤다가 자리를 잡는 등장 곡선. 시작 전(0 이하)은 정확히 0 이라 아직 나오지 않은 소품이 점으로 그려지지 않는다. */
fun easeOutBack(t: Float): Float = if (t <= 0f) 0f else 1f + 2.70158f * (t - 1f).pow(3) + 1.70158f * (t - 1f).pow(2)

/** 2.9초마다 한 번 깜빡인다. 1 은 다 뜬 눈이다. */
fun blinkOpenness(millis: Int): Float {
    val phase = millis % 2900
    return if (phase < 2760) 1f else 1f - 0.9f * sin(PI.toFloat() * (phase - 2760) / 140f)
}

/** 제자리에서 숨 쉬듯 살짝 오르내린다. */
fun stand(
    millis: Int,
    at: Offset,
    look: Offset = Offset.Zero,
) = Move<Nothing>(millis) { f ->
    MascotPose(at.x, at.y, scaleY = 1f + 0.015f * sin(2f * PI.toFloat() * f), look = look)
}

/**
 * 가만히 서서 천천히 숨 쉬며 [glances] 쪽을 차례로 둘러본다. 한 곳을 한참 보다가 다음 곳으로 눈을 옮기고,
 * 끝에는 다시 앞을 본다.
 */
fun idle(
    millis: Int,
    at: Offset,
    glances: List<Offset> = listOf(Offset(-0.8f, -0.2f), Offset(0.8f, -0.2f)),
) = Move<Nothing>(millis) { f ->
    val keys = listOf(Offset.Zero) + glances + Offset.Zero
    val position = f * (keys.size - 1)
    val index = position.toInt().coerceAtMost(keys.size - 2)
    val shift = smooth((position - index - 0.6f) / 0.4f)
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
fun stroll(
    millis: Int,
    from: Offset,
    to: Offset,
    hops: Int,
) = Move<Nothing>(millis) { f ->
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
fun stretch(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
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
fun crouch(
    millis: Int,
    at: Offset,
    depth: Float = 1f,
    look: Offset = Offset.Zero,
) = Move<Nothing>(millis) { f ->
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
fun leap(
    millis: Int,
    from: Offset,
    to: Offset,
    lift: Float,
    spins: Float = 0f,
) = Move<Nothing>(millis) { f ->
    val control = Offset((from.x + to.x) / 2f, min(from.y, to.y) - 2f * lift)
    val position = from * ((1 - f) * (1 - f)) + control * (2 * f * (1 - f)) + to * (f * f)
    val velocity = (control - from) * (2 * (1 - f)) + (to - control) * (2 * f)
    val fastest = maxOf(abs(control.y - from.y), abs(to.y - control.y)) * 2f
    val stretch = if (fastest > 0f) 0.08f * abs(velocity.y) / fastest else 0f
    MascotPose(
        x = position.x,
        y = position.y,
        scaleX = 1f - stretch * 0.6f,
        scaleY = 1f + stretch,
        rotation = spins * 360f * smooth(f),
        look = Offset(sign(to.x - from.x) * 0.8f, sign(velocity.y) * 0.6f),
    )
}

/**
 * 몸 가운데를 축으로 도는 마스코트를 발을 축으로 돈 것처럼 보이게 놓는다. 몸 가운데는 발에서 [size] 의
 * 일정 비율만큼 위에 있어, 돈 만큼 발이 밀려나는 거리를 미리 되돌린다.
 */
fun pinnedAtFeet(
    feet: Offset,
    rotation: Float,
    size: Float,
): MascotPose {
    val radians = Math.toRadians(rotation.toDouble()).toFloat()
    val toCenter = size * MASCOT_CENTER_TO_FEET_RATIO
    return MascotPose(
        x = feet.x + toCenter * sin(radians),
        y = feet.y + toCenter * (1f - cos(radians)),
        rotation = rotation,
    )
}

/**
 * 마스코트가 몸에 붙여 든 소품의 한 점(심벌 viewport 좌표)이 발끝 가운데에서 얼마나 떨어져 있는지 반환한다.
 * 그리기의 변환(발을 붙잡은 찌그러뜨림, 몸 가운데 축 회전)과 같은 순서로 계산해, 소품을 무대에 놓거나 소품
 * 끝을 목표 자리에 맞출 때 쓴다.
 */
fun mascotPointOffset(
    at: Offset,
    rotation: Float,
    scaleX: Float,
    scaleY: Float,
    size: Float,
): Offset {
    val cell = size / MASCOT_VIEWPORT
    val center = (MASCOT_CENTER - MASCOT_BOTTOM) * cell
    val x = (at.x - MASCOT_CENTER) * cell * scaleX
    val y = (at.y - MASCOT_BOTTOM) * cell * scaleY - center
    val radians = Math.toRadians(rotation.toDouble()).toFloat()
    return Offset(
        x * cos(radians) - y * sin(radians),
        center + x * sin(radians) + y * cos(radians),
    )
}

// 화가 소품. 베레모가 얹히는 자리와 기울기, 몸 오른쪽에 쥔 붓의 쥔 자리·붓털이 향하는 회전(0 은 바로 아래)·
// 쥔 자리에서 붓털 끝과 자루 끝까지의 길이다. 자리와 길이는 심벌 viewport 좌표다.
val HatAnchor = Offset(30f, 5.5f)
const val HAT_TILT = -12f
val BrushGrip = Offset(64f, 38f)
const val BRUSH_TILT = -38f
const val BRUSH_TIP_LENGTH = 16f
const val BRUSH_HANDLE_LENGTH = 18f

/**
 * 붓털 끝이 발끝 가운데에서 얼마나 떨어져 있는지 반환한다. 쥔 자리는 몸과 함께 찌그러지고 돌지만, 붓은
 * 찌그러지지 않고 몸 회전과 [brush] 만큼 돈다. 그리기도 같은 순서로 붓을 놓는다.
 */
fun brushTipOffset(
    size: Float,
    rotation: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    brush: Float = 0f,
): Offset {
    val grip = mascotPointOffset(BrushGrip, rotation, scaleX, scaleY, size)
    val radians = Math.toRadians((BRUSH_TILT + rotation + brush).toDouble()).toFloat()
    val length = BRUSH_TIP_LENGTH * size / MASCOT_VIEWPORT
    return grip + Offset(-sin(radians) * length, cos(radians) * length)
}

/** 격자 좌표 꺾은선과 누적 길이. 곡선은 잘게 나눠 길이에 고르게 따라갈 수 있게 한다. */
class BrushPath(
    val points: List<Offset>,
) {
    val lengths: FloatArray =
        FloatArray(points.size).also { lengths ->
            for (i in 1 until points.size) lengths[i] = lengths[i - 1] + (points[i] - points[i - 1]).getDistance()
        }
    val total = lengths.last()

    /** 붓길 길이의 [f] 만큼 간 자리. */
    fun at(f: Float): Offset {
        val target = f.coerceIn(0f, 1f) * total
        var i = 1
        while (i < points.size - 1 && lengths[i] < target) i++
        val span = lengths[i] - lengths[i - 1]
        val t = if (span > 0f) (target - lengths[i - 1]) / span else 0f
        return points[i - 1] + (points[i] - points[i - 1]) * t
    }

    /**
     * [f] 앞뒤로 붓길이 나아가는 방향의 평균. 꺾이거나 되돌아가는 자리에서도 값이 끊기지 않고 지나가도록 짧은
     * 마디 방향을 고르게 평균 내며, 되돌아가는 자리에서는 길이가 0 에 가까워진다.
     */
    fun heading(f: Float): Offset {
        val samples = 12
        val reach = 0.025f
        var sum = Offset.Zero
        var previous = at(f - reach)
        for (step in 1..samples) {
            val next = at(f - reach + 2f * reach * step / samples)
            val length = (next - previous).getDistance()
            if (length > 0f) sum += (next - previous) / length
            previous = next
        }
        return sum / samples.toFloat()
    }
}

private const val CURVE_STEPS = 14
private val PathToken = Regex("[MLQC]|-?\\d*\\.?\\d+")

/** SVG path 문자열(M·L·Q·C 절대 좌표)을 꺾은선으로 바꾼다. 웹과 같은 붓길 문자열을 그대로 쓰기 위해서다. */
fun parseStroke(path: String): BrushPath {
    val tokens = PathToken.findAll(path).map { it.value }.toList()
    val points = mutableListOf<Offset>()
    var index = 0
    var command = ""

    fun read() = tokens[index++].toFloat()

    fun readPoint() = Offset(read(), read())
    while (index < tokens.size) {
        if (tokens[index][0].isLetter()) command = tokens[index++]
        val from = points.lastOrNull()
        when (command) {
            "M", "L" -> points += readPoint()
            "Q" -> {
                val control = readPoint()
                val end = readPoint()
                for (step in 1..CURVE_STEPS) {
                    val t = step / CURVE_STEPS.toFloat()
                    val u = 1f - t
                    points += from!! * (u * u) + control * (2 * t * u) + end * (t * t)
                }
            }
            "C" -> {
                val control1 = readPoint()
                val control2 = readPoint()
                val end = readPoint()
                for (step in 1..CURVE_STEPS) {
                    val t = step / CURVE_STEPS.toFloat()
                    val u = 1f - t
                    points += from!! * (u * u * u) + control1 * (3 * u * u * t) + control2 * (3 * u * t * t) +
                        end * (t * t * t)
                }
            }
            else -> error("Unsupported path command: $command")
        }
    }
    return BrushPath(points)
}

/**
 * 붓털 끝으로 붓길을 따라 긋는다. 붓털 끝을 붓길에 붙잡은 채 나아가는 쪽으로 몸을 기울이되 양 끝에서는 바로
 * 서고, 눈은 붓끝을 본다. [scribble] 이면 지그재그를 휘갈기듯 몸을 좌우로 빠르게 비튼다. [lean] 이 있으면
 * 붓털 끝이 진행 반대쪽으로 끌리게 붓을 눕히고, [press] 가 있으면 붓털을 누른다. 둘 다 양 끝에서 0 으로 모은다.
 *
 * @param toStage 그림 격자 좌표를 무대 좌표로 바꾸는 함수
 * @param lean 끌림의 가장 큰 붓 회전(도)
 * @param press 가장 큰 누름
 */
fun paintAlong(
    millis: Int,
    stroke: BrushPath,
    toStage: (Offset) -> Offset,
    size: Float,
    scribble: Boolean = false,
    lean: Float = 0f,
    press: Float = 0f,
) = Move<Nothing>(millis) { f ->
    val direction = stroke.heading(f)
    val envelope = minOf(1f, f / 0.08f, (1f - f) / 0.08f)
    val wiggle = if (scribble) sin(2f * PI.toFloat() * f * 9f) * envelope else 0f
    val rotation = (12f * direction.x + 6f * wiggle) * envelope
    val scaleX = 1f + 0.06f * wiggle
    val scaleY = 1f - 0.05f * wiggle
    val brush = lean * direction.x * envelope
    val target = toStage(stroke.at(f))
    val offset = brushTipOffset(size, rotation, scaleX, scaleY, brush)
    MascotPose(
        x = target.x - offset.x,
        y = target.y - offset.y,
        scaleX = scaleX,
        scaleY = scaleY,
        rotation = rotation,
        look = Offset(0.7f, 0.6f),
        eyes = if (scribble) MascotEyes.FOCUS else MascotEyes.ROUND,
        brush = brush,
        press = press * envelope,
    )
}

/** 안무 한 막. [kind] 는 그리는 쪽이 막에 맞는 소품을 고를 때 쓴다. */
class Act<out K, out C>(
    val kind: K,
    val moves: List<Move<C>>,
) {
    val millis = moves.sumOf { it.millis }
}

data class MascotMoment<K>(
    val act: K,
    val actMillis: Int,
    val pose: MascotPose,
)

/**
 * 막을 차례로 이어 한 바퀴 안무를 만든다. 시각으로 막·자세를 찾고, 소품이 동작에 맞춰 바뀌는 cue 의 시작과
 * 길이를 막 안의 시각으로 알려 준다. cue 는 안무 안에서 한 번만 쓴다.
 */
class Choreography<K, C>(
    private val acts: List<Act<K, C>>,
) {
    val loopMillis = acts.sumOf { it.millis }

    private val cueSpans: Map<C, Pair<Int, Int>> =
        buildMap {
            for (act in acts) {
                var start = 0
                for (move in act.moves) {
                    move.cue?.let { put(it, start to move.millis) }
                    start += move.millis
                }
            }
        }

    /** 한 바퀴 안의 시각에 마스코트가 어느 막에서 어떤 자세인지. */
    fun momentAt(timeMillis: Int): MascotMoment<K> {
        val time = timeMillis.mod(loopMillis)
        var actStart = 0
        for (act in acts) {
            if (time < actStart + act.millis) {
                val inAct = time - actStart
                var moveStart = 0
                for (move in act.moves) {
                    if (inAct < moveStart + move.millis) {
                        return MascotMoment(act.kind, inAct, move.pose((inAct - moveStart) / move.millis.toFloat()))
                    }
                    moveStart += move.millis
                }
            }
            actStart += act.millis
        }
        error("$time is outside the loop")
    }

    /** [kind] 막(같은 막이 여럿이면 첫 막)의 길이. */
    fun actMillis(kind: K): Int = acts.first { it.kind == kind }.millis

    /** [kind] 막(같은 막이 여럿이면 첫 막)이 한 바퀴 안에서 시작하는 시각. */
    fun actStart(kind: K): Int = acts.takeWhile { it.kind != kind }.sumOf { it.millis }

    /** [cue] 동작이 막 안에서 시작하는 시각. */
    fun cueStart(cue: C): Int = cueSpans.getValue(cue).first

    /** [cue] 동작의 길이. */
    fun cueMillis(cue: C): Int = cueSpans.getValue(cue).second

    /** [cue] 동작이 [actMillis] 에 얼마나 진행했는지(0..1). */
    fun cueProgress(
        cue: C,
        actMillis: Int,
    ): Float {
        val (start, millis) = cueSpans.getValue(cue)
        return ((actMillis - start) / millis.toFloat()).coerceIn(0f, 1f)
    }
}
