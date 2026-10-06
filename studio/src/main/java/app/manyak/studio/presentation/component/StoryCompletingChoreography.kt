// 안무 값은 장면마다 한 번씩만 쓰이는 연출 수치라 이름을 붙이면 오히려 동작이 읽히지 않는다. 이 장면만의
// 낱동작을 막과 함께 둔다.
@file:Suppress("MagicNumber", "TooManyFunctions")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.Offset
import app.manyak.designsystem.component.MASCOT_HEIGHT_RATIO
import app.manyak.designsystem.component.MASCOT_WIDTH_RATIO
import app.manyak.designsystem.mascot.Act
import app.manyak.designsystem.mascot.BrushPath
import app.manyak.designsystem.mascot.Choreography
import app.manyak.designsystem.mascot.MascotEyes
import app.manyak.designsystem.mascot.MascotPose
import app.manyak.designsystem.mascot.Move
import app.manyak.designsystem.mascot.brushTipOffset
import app.manyak.designsystem.mascot.crouch
import app.manyak.designsystem.mascot.cue
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.eyes
import app.manyak.designsystem.mascot.idle
import app.manyak.designsystem.mascot.leap
import app.manyak.designsystem.mascot.paintAlong
import app.manyak.designsystem.mascot.parseStroke
import app.manyak.designsystem.mascot.smooth
import app.manyak.designsystem.mascot.stand
import app.manyak.designsystem.mascot.stretch
import app.manyak.designsystem.mascot.stroll
import kotlin.math.PI
import kotlin.math.sin

/*
 * 스토리 완성 중 표지에서 마스코트가 연기하는 안무. 키워드를 고르고, 스토리라인을 고르고, 졸다가 에너지
 * 드링크를 마시고 원고를 터보로 쓴 뒤, 베레모를 쓰고 붓으로 주인공 인물화를 그리고 소품을 하늘로 던지며
 * 뛰어다니는 순서로 제작 과정을 따라가고, 바쁜 막 사이마다 쉰다.
 *
 * 좌표는 표지 폭을 1 로 둔 값이다(3:4 라 높이는 4/3). 막은 모두 바닥 가운데([Home])에서 시작해 같은 자리로
 * 돌아와, 막의 순서를 바꾸거나 막을 빼도 이음매에서 마스코트가 순간이동하지 않는다. 인물화의 붓길은 캔버스
 * 폭을 84칸으로 둔 격자의 SVG path 문자열로 적는다. 웹과 같은 값을 쓴다.
 */

internal const val STAGE_HEIGHT = 4f / 3f
internal const val MASCOT_SIZE = 0.24f
internal const val FLOOR = STAGE_HEIGHT - 0.11f
internal const val MASCOT_HALF_WIDTH = MASCOT_SIZE * MASCOT_WIDTH_RATIO / 2f
internal const val MASCOT_HEIGHT = MASCOT_SIZE * MASCOT_HEIGHT_RATIO
private const val WALL_GAP = 0.05f

internal val Home = Offset(0.5f, FLOOR)

/** [INTERLUDE] 는 막 사이에 숨을 돌리는 쉼이다. 소품 없이 가만히 있거나 천천히 걷는다. */
internal enum class CompletingAct { KEYWORDS, STORYLINE, ENERGY, TYPING, PAINTING, BOUNCING, WALL_JUMP, INTERLUDE }

/** 소품이 마스코트의 동작에 맞춰 바뀌는 순간. */
internal enum class Cue {
    KEYWORD_1,
    KEYWORD_2,
    KEYWORD_3,
    KEYWORDS_LEAVE,
    STORYLINE_1_LOOK,
    STORYLINE_3_LOOK,
    STORYLINE_PICK,
    DOZE,
    CAN_DROP,
    CAN_CATCH,
    DRINK,
    POWER_UP,
    TYPING,
    TYPING_DONE,
    HAT,
    BRUSH,
    MOON,
    HAIR,
    FACE,
    BLUSH,
    SCARF,
    SIGNATURE,
    TOSS,
}

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

// 에너지 드링크 — 몸 오른쪽에 쥐는 자리와, 마실 때 얼굴 앞으로 들어 기울이는 자리·기울기(심벌 viewport 좌표, 도).
// 캔은 위에서 떨어지고, 점프의 CAN_CATCH 비율에서 손에 들어온다.
internal val CanGrip = Offset(64f, 40f)
internal val CanSip = Offset(42f, 20f)
internal const val CAN_SIP_TILT = -125f
internal const val CAN_CATCH = 0.55f
internal val CanDropFrom = Offset(Home.x + 0.14f, -0.12f)

// 타자 — 에너지 드링크를 마셔 빠르게 두드리는 두 줄 7칸과 스페이스 바.
internal const val KEYSTROKE_MILLIS = 80
internal const val KEYSTROKES = 20
internal const val KEY_COLUMNS = 7
internal const val KEY_COUNT = KEY_COLUMNS * 2 + 1

// 인물화 — 이젤 위 세로 캔버스. 격자 한 칸은 표지 폭의 0.6/84 다.
internal const val CANVAS_GRID = 84f
internal const val CANVAS_WIDTH = 0.6f
internal const val CANVAS_LEFT = 0.2f
internal const val CANVAS_TOP = 0.1f
internal const val CANVAS_BOTTOM = CANVAS_TOP + CANVAS_WIDTH * 4f / 3f

/** 캔버스 격자 좌표를 표지 좌표로 바꾼다. */
internal fun onCanvas(at: Offset) = Offset(CANVAS_LEFT, CANVAS_TOP) + at * (CANVAS_WIDTH / CANVAS_GRID)

/** 붓을 집기 전 바닥에 누운 붓의 쥔 자리. 붓털이 오른쪽을 향한다. */
internal val BrushOnFloor = Offset(0.68f, FLOOR - 0.008f)

/** 붓을 낚아채는 순간. 점프의 이 비율에서 붓이 손에 들어온다. */
internal const val BRUSH_CATCH = 0.65f

/*
 * 주인공 인물화. 오른쪽을 보는 옆얼굴이 큰 달을 등지고, 머리카락과 목도리가 바람에 왼쪽으로 날린다. 달을 찍어
 * 번지게 하고, 머리를 굵은 붓으로 쓸고, 얼굴 옆선을 가늘게 긋고, 볼을 찍고, 목도리를 두르고, 구석에 서명한다.
 */
internal val PortraitMoon = Offset(58f, 30f)
internal val PortraitHair =
    parseStroke(
        "M53,33 C48,24 34,22 27,30 C22,34 16,33 12,33 C8,33 8,37 12,38 C16,39 19,41 20,45 C15,49 12,51 10,52 " +
            "C7,53 7,57 11,57 C15,57 20,58 23,60 C25,63 28,64 31,65",
    )
internal val PortraitFace =
    parseStroke(
        "M53,33 Q57,37 56,41 L62,48.5 L57,50.5 Q59.5,52.5 57.5,54.5 Q59.5,56.5 57,58 Q58,62 53,63.5 " +
            "Q49,64.5 46,64 L45,76",
    )
internal val PortraitBlush = Offset(51f, 55f)
internal val PortraitScarf = parseStroke("M60,73 C52,80 41,80 33,76 C25,72 18,75 10,72")
internal val PortraitSignature = parseStroke("M58,101 C60,97 62,105 65,100 C67,96 69,103 72,99")

/**
 * 벽에 철썩 붙어 미끄러지다가 다시 웅크려 튀어 나간다. 벽 쪽 가장자리가 벽에 닿도록 찌그러진 폭만큼
 * 발끝을 옮긴다.
 */
private fun cling(
    millis: Int,
    onLeft: Boolean,
    from: Float,
    to: Float,
) = Move<Nothing>(millis) { f ->
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

/** 졸린 눈으로 두 번 꾸벅인다. 고개가 천천히 떨어졌다가 화들짝 돌아온다. */
private fun doze(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val phase = (f * 2f) % 1f
    val nod = if (phase < 0.8f) smooth(phase / 0.8f) else 1f - smooth((phase - 0.8f) / 0.2f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f + 0.03f * nod,
        scaleY = 1f - 0.05f * nod,
        rotation = 9f * nod,
        look = Offset(0.2f, 0.8f),
        eyes = MascotEyes.SLEEPY,
    )
}

/** 마시는 진행 [sip] 에서 꿀꺽 넘기는 정도(-1..1). 세 모금이다. */
internal fun gulpAt(sip: Float) = if (sip > 0.2f && sip < 0.88f) sin((sip - 0.2f) / 0.68f * PI.toFloat() * 6f) else 0f

/** 캔을 얼굴 앞으로 들어 꿀꺽꿀꺽 세 모금 마신다. 몸을 살짝 젖히고 모금마다 몸이 오르내린다. */
private fun drink(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val lean = smooth(f / 0.2f) * (1f - smooth((f - 0.9f) / 0.1f))
    val gulp = gulpAt(f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f - 0.025f * gulp,
        scaleY = 1f + 0.035f * gulp,
        rotation = -7f * lean,
        look = Offset(0.3f, -0.6f),
        eyes = MascotEyes.SMILE,
    )
}

/** 에너지가 차오른다. 쭉 늘어나며 부르르 떨고 눈이 별처럼 반짝인다. */
private fun powerUp(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val rise = smooth(f / 0.25f) * (1f - smooth((f - 0.75f) / 0.25f))
    val shake = sin(2f * PI.toFloat() * f * millis / 38f) * 0.006f * rise
    MascotPose(
        x = at.x + shake,
        y = at.y,
        scaleX = 1f - 0.08f * rise,
        scaleY = 1f + 0.12f * rise,
        look = Offset(0f, -0.2f),
        eyes = MascotEyes.SPARKLE,
    )
}

/** 어지러워 고개를 좌우로 휘청이다가 차츰 바로 선다. */
private fun wobble(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    MascotPose(
        x = at.x,
        y = at.y,
        scaleY = 1f - 0.03f * sin(PI.toFloat() * f),
        rotation = 12f * sin(2f * PI.toFloat() * f * 2f) * (1f - f),
        eyes = MascotEyes.DIZZY,
    )
}

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
        eyes = MascotEyes.FOCUS,
    )
}

/** 매번 다른 키를 고르되 같은 시간에는 늘 같은 키다. 마지막 칸은 스페이스 바다. */
internal fun keyFor(stroke: Int): Int = (stroke * 11 + stroke * stroke * 3 + 4) % KEY_COUNT

/** 키가 키보드 가운데에서 얼마나 왼쪽(-1)·오른쪽(1)에 있는지. */
internal fun keySide(key: Int): Float = if (key == KEY_COUNT - 1) 0f else (key % KEY_COLUMNS - 3) / 3f

/** 바로 선 마스코트의 붓털 끝이 캔버스 격자 [at] 에 닿는 발끝 자리. */
private fun brushAt(at: Offset): Offset = onCanvas(at) - brushTipOffset(MASCOT_SIZE)

/** 붓길의 시작점이나 끝점에 붓털 끝이 닿는 발끝 자리. */
private fun BrushPath.tip(end: Boolean = false) = brushAt(at(if (end) 1f else 0f))

/** 캔버스 붓길을 붓털 끝으로 따라 긋는다. */
private fun paint(
    millis: Int,
    stroke: BrushPath,
) = paintAlong(millis, stroke, ::onCanvas, MASCOT_SIZE)

/** 붓길 사이를 짧게 건너뛴다. */
private fun hop(
    millis: Int,
    from: Offset,
    to: Offset,
) = leap(millis, from, to, lift = 0.035f)

private val FirstChip = KeywordChips[PickedChips[0].first].standing
private val SecondChip = KeywordChips[PickedChips[1].first].standing
private val ThirdChip = KeywordChips[PickedChips[2].first].standing
private val UnderFirst = Offset(0.19f, FLOOR)
private val UnderThird = Offset(0.81f, FLOOR)
private val HopLeft = Offset(0.2f, FLOOR)
private val HopRight = Offset(0.8f, FLOOR)
private val Strolled = Offset(0.74f, FLOOR)
private val StrolledLeft = Offset(0.26f, FLOOR)
private const val WALL_LEFT = WALL_GAP + MASCOT_HALF_WIDTH
private const val WALL_RIGHT = 1f - WALL_GAP - MASCOT_HALF_WIDTH
private val Moon = brushAt(PortraitMoon)
private val Blush = brushAt(PortraitBlush)
private val LookUp = Offset(0f, -1f)

private fun interlude(vararg moves: Move<Nothing>) = Act(CompletingAct.INTERLUDE, moves.toList())

private val KeywordsAct =
    Act(
        CompletingAct.KEYWORDS,
        listOf(
            stand(360, Home, look = LookUp),
            crouch(140, Home),
            leap(380, Home, FirstChip, lift = 0.1f),
            crouch(150, FirstChip).cue(Cue.KEYWORD_1).eyes(MascotEyes.SMILE),
            leap(340, FirstChip, SecondChip, lift = 0.1f),
            crouch(150, SecondChip).cue(Cue.KEYWORD_2).eyes(MascotEyes.SMILE),
            leap(340, SecondChip, ThirdChip, lift = 0.1f),
            crouch(170, ThirdChip, depth = 1.2f).cue(Cue.KEYWORD_3).eyes(MascotEyes.SMILE),
            stand(320, ThirdChip, look = Offset(-0.6f, 0.8f)).eyes(MascotEyes.WINK),
            leap(480, ThirdChip, Home, lift = 0.06f).cue(Cue.KEYWORDS_LEAVE),
            crouch(160, Home),
            stand(240, Home),
        ),
    )

private val StorylineAct =
    Act(
        CompletingAct.STORYLINE,
        listOf(
            stand(460, Home, look = LookUp),
            leap(320, Home, UnderFirst, lift = 0.08f),
            stand(440, UnderFirst, look = LookUp).cue(Cue.STORYLINE_1_LOOK),
            leap(440, UnderFirst, UnderThird, lift = 0.14f),
            stand(400, UnderThird, look = LookUp).cue(Cue.STORYLINE_3_LOOK),
            leap(320, UnderThird, Home, lift = 0.06f),
            crouch(180, Home, look = LookUp),
            leap(560, Home, Home, lift = HEADBUTT_LIFT).cue(Cue.STORYLINE_PICK),
            crouch(160, Home).eyes(MascotEyes.SPARKLE),
            stand(560, Home, look = LookUp).eyes(MascotEyes.SPARKLE),
        ),
    )

private val EnergyAct =
    Act(
        CompletingAct.ENERGY,
        listOf(
            doze(1000, Home).cue(Cue.DOZE),
            stand(280, Home, look = LookUp).cue(Cue.CAN_DROP),
            crouch(110, Home, look = Offset(0.4f, -0.8f)),
            leap(380, Home, Home, lift = 0.1f).cue(Cue.CAN_CATCH),
            crouch(120, Home),
            drink(1250, Home).cue(Cue.DRINK),
            powerUp(800, Home).cue(Cue.POWER_UP),
        ),
    )

private val TypingAct =
    Act(
        CompletingAct.TYPING,
        listOf(
            stand(300, Home, look = Offset(0f, 0.8f)).eyes(MascotEyes.SPARKLE),
            Move(KEYSTROKE_MILLIS * KEYSTROKES, Cue.TYPING) { f -> typingPose(f * KEYSTROKE_MILLIS * KEYSTROKES) },
            crouch(140, Home).cue(Cue.TYPING_DONE),
            leap(360, Home, Home, lift = 0.16f).eyes(MascotEyes.SMILE),
            crouch(120, Home).eyes(MascotEyes.SMILE),
        ),
    )

private val PaintingAct =
    Act(
        CompletingAct.PAINTING,
        listOf(
            stand(320, Home, look = LookUp).cue(Cue.HAT),
            crouch(140, Home, depth = 1.1f).eyes(MascotEyes.SMILE),
            stand(160, Home, look = Offset(0.8f, 0.8f)),
            crouch(110, Home, look = Offset(0.8f, 0.8f)),
            leap(360, Home, Home, lift = 0.08f).cue(Cue.BRUSH),
            crouch(110, Home),
            leap(420, Home, Moon, lift = 0.1f),
            crouch(240, Moon, depth = 1.2f).cue(Cue.MOON),
            hop(150, Moon, PortraitHair.tip()),
            paint(800, PortraitHair).cue(Cue.HAIR),
            hop(170, PortraitHair.tip(end = true), PortraitFace.tip()),
            paint(560, PortraitFace).cue(Cue.FACE),
            hop(140, PortraitFace.tip(end = true), Blush),
            crouch(180, Blush, depth = 0.9f).cue(Cue.BLUSH),
            hop(150, Blush, PortraitScarf.tip()),
            paint(420, PortraitScarf).cue(Cue.SCARF),
            hop(160, PortraitScarf.tip(end = true), PortraitSignature.tip()),
            paint(360, PortraitSignature).cue(Cue.SIGNATURE),
            leap(420, PortraitSignature.tip(end = true), Home, lift = 0.08f),
            crouch(140, Home),
            stand(260, Home, look = Offset(-0.4f, -0.7f)).eyes(MascotEyes.WINK),
            crouch(150, Home, depth = 1.2f),
            leap(560, Home, Home, lift = 0.22f).cue(Cue.TOSS).eyes(MascotEyes.SMILE),
            crouch(160, Home, depth = 1.1f).eyes(MascotEyes.SMILE),
            stand(420, Home, look = LookUp).eyes(MascotEyes.SMILE),
        ),
    )

private val BouncingAct =
    Act(
        CompletingAct.BOUNCING,
        listOf(
            crouch(140, Home),
            leap(420, Home, HopLeft, lift = 0.28f).eyes(MascotEyes.SMILE),
            crouch(130, HopLeft),
            leap(680, HopLeft, HopRight, lift = 0.85f, spins = 1f).eyes(MascotEyes.SMILE),
            crouch(170, HopRight, depth = 1.2f).eyes(MascotEyes.DIZZY),
            wobble(360, HopRight),
            leap(360, HopRight, Home, lift = 0.2f),
            crouch(120, Home),
            leap(240, Home, Home, lift = 0.1f).eyes(MascotEyes.SMILE),
            crouch(100, Home),
            stand(260, Home),
        ),
    )

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
            crouch(180, Home, depth = 1.3f).eyes(MascotEyes.DIZZY),
            wobble(420, Home),
            stand(280, Home),
        ),
    )

// 막은 위에서 정의한 값으로 만들어지므로 파일 맨 끝에서 묶는다. 바쁜 막 사이마다 쉼을 두어, 고르고 쓰고
// 그리는 일이 숨 가쁘게 이어지지 않고 한 장면씩 끝나는 느낌을 준다.
private val Completing =
    Choreography(
        listOf(
            KeywordsAct,
            interlude(idle(1100, Home)),
            StorylineAct,
            interlude(
                stroll(1500, Home, Strolled, hops = 4).eyes(MascotEyes.SMILE),
                idle(1000, Strolled, glances = listOf(Offset(-0.8f, -0.3f))),
                stroll(1150, Strolled, Home, hops = 3).eyes(MascotEyes.SMILE),
            ),
            EnergyAct,
            TypingAct,
            interlude(stretch(900, Home), idle(600, Home, glances = emptyList())),
            PaintingAct,
            interlude(
                stroll(1150, Home, StrolledLeft, hops = 3).eyes(MascotEyes.SMILE),
                idle(900, StrolledLeft, glances = listOf(Offset(0.8f, -0.6f))),
                stroll(1150, StrolledLeft, Home, hops = 3).eyes(MascotEyes.SMILE),
            ),
            BouncingAct,
            WallJumpAct,
            interlude(idle(1400, Home, glances = listOf(Offset(0.8f, 0f), Offset(-0.8f, 0f), Offset(0f, 0.8f)))),
        ),
    )

internal val CompletingLoopMillis = Completing.loopMillis

internal fun completingMoment(timeMillis: Int) = Completing.momentAt(timeMillis)

/** [cue] 동작이 막 안에서 시작하는 시각. */
internal fun cueStart(cue: Cue): Int = Completing.cueStart(cue)

/** [cue] 동작의 길이. */
internal fun cueMillis(cue: Cue): Int = Completing.cueMillis(cue)

/** [cue] 동작이 [actMillis] 에 얼마나 진행했는지(0..1). */
internal fun cueProgress(
    cue: Cue,
    actMillis: Int,
): Float = Completing.cueProgress(cue, actMillis)

/** 몇 번째 키를 누르는지와, 그 안에서 얼마나 진행했는지(0..1). */
internal fun keystrokeAt(actMillis: Int): Pair<Int, Float> {
    val typing = (actMillis - cueStart(Cue.TYPING)).coerceIn(0, KEYSTROKE_MILLIS * KEYSTROKES - 1)
    return typing / KEYSTROKE_MILLIS to (typing % KEYSTROKE_MILLIS) / KEYSTROKE_MILLIS.toFloat()
}

/**
 * 소품이 막 시작에 살짝 넘치며 나타나고 막 끝에 사라지는 정도. 1 을 넘는 값은 등장 때의 넘침이다.
 * [enterDelay] 만큼 늦게 나타나 여러 소품이 차례로 튀어나온다.
 */
internal fun propVisibility(
    act: CompletingAct,
    actMillis: Int,
    enterDelay: Int = 0,
): Float {
    val enter = easeOutBack(((actMillis - enterDelay) / 320f).coerceIn(0f, 1f))
    val exit = ((actMillis - (Completing.actMillis(act) - 320)) / 320f).coerceIn(0f, 1f)
    return enter * (1f - exit)
}
