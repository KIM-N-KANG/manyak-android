// 안무 값은 장면마다 한 번씩만 쓰이는 연출 수치라 이름을 붙이면 오히려 동작이 읽히지 않는다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/*
 * 스토리 완성 중 표지에서 마스코트가 연기하는 안무. 키워드를 고르고, 스토리라인을 고르고, 원고를 쓰고,
 * 그림을 그린 뒤 신나서 뛰어다니는 순서로 제작 과정을 따라간다. 낱동작([MascotMoves.kt])을 막으로 엮고,
 * 시간 → 자세가 순수 함수라 그리는 쪽은 매 프레임 이 값을 읽기만 한다.
 *
 * 막은 모두 바닥 가운데([Home])에서 시작해 같은 자리로 돌아온다. 그래서 막의 순서를 바꾸거나 막을 빼도
 * 이음매에서 마스코트가 순간이동하지 않는다.
 */

internal val Home = Offset(0.5f, FLOOR)

/** [INTERLUDE] 는 막 사이에 숨을 돌리는 쉼이다. 소품 없이 가만히 있거나 천천히 걷는다. */
internal enum class CompletingAct { KEYWORDS, STORYLINE, TYPING, PAINTING, BOUNCING, WALL_JUMP, INTERLUDE }

/** 소품이 마스코트의 동작에 맞춰 바뀌는 순간. */
internal enum class Cue {
    KEYWORD_1,
    KEYWORD_2,
    KEYWORD_3,
    KEYWORDS_LEAVE,
    STORYLINE_1_LOOK,
    STORYLINE_3_LOOK,
    STORYLINE_PICK,
    TYPING,
    TYPING_DONE,
    STROKE_1,
    STROKE_2,
    BLOOM,
    SIGNATURE,
}

internal data class CompletingMoment(
    val act: CompletingAct,
    val actMillis: Int,
    val pose: MascotPose,
)

// 키워드 — 칩을 발판 삼아 아래 줄부터 밟고 올라가며 하나씩 고른다.
internal const val CHIP_HEIGHT = 0.09f

internal class KeywordChip(
    val left: Float,
    val top: Float,
    val right: Float,
) {
    val standing get() = Offset((left + right) / 2f, top)
}

internal val KeywordChips =
    listOf(
        KeywordChip(0.12f, 0.34f, 0.44f),
        KeywordChip(0.50f, 0.34f, 0.88f),
        KeywordChip(0.12f, 0.58f, 0.58f),
        KeywordChip(0.64f, 0.58f, 0.88f),
        KeywordChip(0.12f, 0.82f, 0.36f),
        KeywordChip(0.42f, 0.82f, 0.70f),
        KeywordChip(0.76f, 0.82f, 0.88f),
    )

/** 밟는 순서대로 고르는 칩과 그 순간. */
internal val PickedChips = listOf(5 to Cue.KEYWORD_1, 2 to Cue.KEYWORD_2, 1 to Cue.KEYWORD_3)

// 스토리라인 — 세 장을 차례로 올려다보다가 가운데 카드를 머리로 받아 고른다.
internal const val STORYLINE_TOP = 0.30f
internal const val STORYLINE_BOTTOM = 0.70f
internal const val STORYLINE_WIDTH = 0.26f
internal val StorylineLefts = floatArrayOf(0.06f, 0.37f, 0.68f)
internal const val PICKED_STORYLINE = 1

/** 점프 꼭대기에서 머리가 카드 아래 변에 닿는 높이. 머리가 닿는 순간은 점프의 한가운데다. */
private const val HEADBUTT_LIFT = FLOOR - STORYLINE_BOTTOM - MASCOT_HEIGHT

// 타자 — 키보드 앞에 앉아 원고를 쓴다.
internal const val KEYSTROKE_MILLIS = 120
internal const val KEYSTROKES = 20
internal const val KEY_COLUMNS = 7

/** 두 줄 7칸 + 스페이스 바. */
internal const val KEY_COUNT = KEY_COLUMNS * 2 + 1

// 그림 — 액자에 붓질 두 번, 물감 한 번 찍고, 구석에 서명한다.
internal val Stroke1 = Cubic(Offset(0.18f, 0.54f), Offset(0.36f, 0.36f), Offset(0.58f, 0.68f), Offset(0.82f, 0.46f))
internal val Stroke2 = Cubic(Offset(0.80f, 0.62f), Offset(0.62f, 0.52f), Offset(0.42f, 0.70f), Offset(0.20f, 0.63f))
internal val Signature = Cubic(Offset(0.68f, 0.69f), Offset(0.71f, 0.63f), Offset(0.75f, 0.74f), Offset(0.80f, 0.66f))
internal val BloomCenter = Offset(0.66f, 0.27f)

private const val WALL_LEFT = WALL_GAP + MASCOT_HALF_WIDTH
private const val WALL_RIGHT = 1f - WALL_GAP - MASCOT_HALF_WIDTH

private class Act(
    val kind: CompletingAct,
    val moves: List<Move>,
) {
    val millis = moves.sumOf { it.millis }
}

private class CueSpan(
    val start: Int,
    val millis: Int,
)

internal fun completingMoment(timeMillis: Int): CompletingMoment {
    var actStart = 0
    val time = timeMillis.mod(CompletingLoopMillis)
    for (act in Acts) {
        if (time < actStart + act.millis) {
            val actMillis = time - actStart
            var moveStart = 0
            for (move in act.moves) {
                if (actMillis < moveStart + move.millis) {
                    val fraction = (actMillis - moveStart) / move.millis.toFloat()
                    return CompletingMoment(act.kind, actMillis, move.pose(fraction))
                }
                moveStart += move.millis
            }
        }
        actStart += act.millis
    }
    error("$time is outside the loop")
}

/** 막별 길이. 소품이 막 끝에서 사라질 때 쓴다. */
internal fun completingActMillis(act: CompletingAct): Int = Acts.first { it.kind == act }.millis

/** [cue] 동작이 막 안에서 시작하는 시각. */
internal fun cueStart(cue: Cue): Int = CueSpans.getValue(cue).start

/** [cue] 동작의 길이. */
internal fun cueMillis(cue: Cue): Int = CueSpans.getValue(cue).millis

/** [cue] 동작이 [actMillis] 에 얼마나 진행했는지(0..1). */
internal fun cueProgress(
    cue: Cue,
    actMillis: Int,
): Float {
    val span = CueSpans.getValue(cue)
    return ((actMillis - span.start) / span.millis.toFloat()).coerceIn(0f, 1f)
}

/** 몇 번째 키를 누르는지와, 그 안에서 얼마나 진행했는지(0..1). */
internal fun keystrokeAt(actMillis: Int): Pair<Int, Float> {
    val typing = (actMillis - cueStart(Cue.TYPING)).coerceIn(0, KEYSTROKE_MILLIS * KEYSTROKES - 1)
    return typing / KEYSTROKE_MILLIS to (typing % KEYSTROKE_MILLIS) / KEYSTROKE_MILLIS.toFloat()
}

/** 매번 다른 키를 고르되 같은 시간에는 늘 같은 키다. 마지막 칸은 스페이스 바다. */
internal fun keyFor(stroke: Int): Int = (stroke * 11 + stroke * stroke * 3 + 4) % KEY_COUNT

/** 키가 키보드 가운데에서 얼마나 왼쪽(-1)·오른쪽(1)에 있는지. */
internal fun keySide(key: Int): Float = if (key == KEY_COUNT - 1) 0f else (key % KEY_COLUMNS - 3) / 3f

private val KeywordsAct =
    Act(
        CompletingAct.KEYWORDS,
        PickedChips.map { (index, _) -> KeywordChips[index].standing }.let { (first, second, third) ->
            listOf(
                stand(360, Home, look = Offset(0f, -1f)),
                crouch(140, Home),
                leap(380, Home, first, lift = 0.1f),
                crouch(150, first).cue(Cue.KEYWORD_1),
                leap(340, first, second, lift = 0.1f),
                crouch(150, second).cue(Cue.KEYWORD_2),
                leap(340, second, third, lift = 0.1f),
                crouch(170, third, depth = 1.2f).cue(Cue.KEYWORD_3),
                stand(320, third, look = Offset(-0.6f, 0.8f)),
                leap(480, third, Home, lift = 0.06f).cue(Cue.KEYWORDS_LEAVE),
                crouch(160, Home),
                stand(240, Home),
            )
        },
    )

private val StorylineAct =
    Offset(0.19f, FLOOR).let { underFirst ->
        val underThird = Offset(0.81f, FLOOR)
        Act(
            CompletingAct.STORYLINE,
            listOf(
                stand(460, Home, look = Offset(0f, -1f)),
                leap(320, Home, underFirst, lift = 0.08f),
                stand(440, underFirst, look = Offset(0f, -1f)).cue(Cue.STORYLINE_1_LOOK),
                leap(440, underFirst, underThird, lift = 0.14f),
                stand(400, underThird, look = Offset(0f, -1f)).cue(Cue.STORYLINE_3_LOOK),
                leap(320, underThird, Home, lift = 0.06f),
                crouch(180, Home, look = Offset(0f, -1f)),
                leap(560, Home, Home, lift = HEADBUTT_LIFT).cue(Cue.STORYLINE_PICK),
                crouch(160, Home),
                stand(560, Home, look = Offset(0f, -1f)),
            ),
        )
    }

private val TypingAct =
    Act(
        CompletingAct.TYPING,
        listOf(
            stand(360, Home, look = Offset(0f, 0.8f)),
            Move(KEYSTROKE_MILLIS * KEYSTROKES, Cue.TYPING) { f -> typingPose(f * KEYSTROKE_MILLIS * KEYSTROKES) },
            crouch(140, Home).cue(Cue.TYPING_DONE),
            leap(360, Home, Home, lift = 0.16f),
            crouch(120, Home),
        ),
    )

/** 누를 키 쪽으로 몸을 숙이며 내리찍는다. 키를 누르는 순간(한 타의 가운데)에 가장 깊다. */
private fun typingPose(millis: Float): MascotPose {
    val stroke = (millis / KEYSTROKE_MILLIS).toInt()
    val dip = sin(PI.toFloat() * (millis % KEYSTROKE_MILLIS) / KEYSTROKE_MILLIS)
    val side = keySide(keyFor(stroke))
    return MascotPose(
        x = Home.x + side * 0.02f * dip,
        y = Home.y + 0.01f * dip,
        scaleX = 1f + 0.05f * dip,
        scaleY = 1f - 0.07f * dip,
        rotation = side * 8f * dip,
        look = Offset(side * 0.7f, 0.9f),
        squint = 0.2f * dip,
    )
}

private val PaintingAct =
    Act(
        CompletingAct.PAINTING,
        listOf(
            stand(320, Home, look = Offset(0f, -1f)),
            crouch(140, Home),
            leap(420, Home, Stroke1.start, lift = 0.12f),
            brush(820, Stroke1).cue(Cue.STROKE_1),
            leap(260, Stroke1.end, Stroke2.start, lift = 0.05f),
            brush(720, Stroke2).cue(Cue.STROKE_2),
            leap(400, Stroke2.end, BloomCenter, lift = 0.08f),
            crouch(240, BloomCenter, depth = 1.3f).cue(Cue.BLOOM),
            leap(340, BloomCenter, Signature.start, lift = 0.06f),
            brush(380, Signature).cue(Cue.SIGNATURE),
            leap(420, Signature.end, Home, lift = 0.08f),
            crouch(160, Home),
            stand(560, Home, look = Offset(0f, -1f)),
        ),
    )

private val BouncingAct =
    Offset(0.2f, FLOOR).let { left ->
        val right = Offset(0.8f, FLOOR)
        Act(
            CompletingAct.BOUNCING,
            listOf(
                crouch(140, Home),
                leap(420, Home, left, lift = 0.28f),
                crouch(130, left),
                leap(680, left, right, lift = 0.85f, spins = 1f),
                crouch(170, right, depth = 1.2f),
                leap(360, right, Home, lift = 0.2f),
                crouch(120, Home),
                leap(240, Home, Home, lift = 0.1f),
                crouch(100, Home),
                stand(260, Home),
            ),
        )
    }

private val WallJumpAct =
    Act(
        CompletingAct.WALL_JUMP,
        listOf(
            crouch(150, Home, depth = 1.2f),
            leap(380, Home, Offset(WALL_LEFT, 0.80f), lift = 0.18f),
            cling(520, onLeft = true, from = 0.80f, to = 0.90f),
            leap(440, Offset(WALL_LEFT, 0.90f), Offset(WALL_RIGHT, 0.60f), lift = 0.16f),
            cling(480, onLeft = false, from = 0.60f, to = 0.70f),
            leap(440, Offset(WALL_RIGHT, 0.70f), Home, lift = 0.14f, spins = -1f),
            crouch(180, Home, depth = 1.3f),
            stand(280, Home),
        ),
    )

private fun interlude(vararg moves: Move) = Act(CompletingAct.INTERLUDE, moves.toList())

private val Strolled = Offset(0.74f, FLOOR)
private val StrolledLeft = Offset(0.26f, FLOOR)

// 막은 위에서 정의한 값으로 만들어지므로 파일 맨 끝에서 묶는다. 바쁜 막 사이마다 쉼을 두어, 고르고 쓰고
// 그리는 일이 숨 가쁘게 이어지지 않고 한 장면씩 끝나는 느낌을 준다.
private val Acts =
    listOf(
        KeywordsAct,
        interlude(idle(1100, Home)),
        StorylineAct,
        interlude(
            stroll(1500, Home, Strolled, hops = 4),
            idle(1000, Strolled, glances = listOf(Offset(-0.8f, -0.3f))),
            stroll(1150, Strolled, Home, hops = 3),
        ),
        TypingAct,
        interlude(stretch(900, Home), idle(600, Home, glances = emptyList())),
        PaintingAct,
        interlude(
            stroll(1150, Home, StrolledLeft, hops = 3),
            idle(900, StrolledLeft, glances = listOf(Offset(0.8f, -0.6f))),
            stroll(1150, StrolledLeft, Home, hops = 3),
        ),
        BouncingAct,
        WallJumpAct,
        interlude(idle(1400, Home, glances = listOf(Offset(0.8f, 0f), Offset(-0.8f, 0f), Offset(0f, 0.8f)))),
    )

internal val CompletingLoopMillis = Acts.sumOf { it.millis }

private val CueSpans: Map<Cue, CueSpan> =
    buildMap {
        for (act in Acts) {
            var start = 0
            for (move in act.moves) {
                move.cue?.let { put(it, CueSpan(start, move.millis)) }
                start += move.millis
            }
        }
    }
