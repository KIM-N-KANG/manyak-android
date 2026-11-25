// 안무 값은 장면마다 한 번씩만 쓰이는 연출 수치라 이름을 붙이면 오히려 동작이 읽히지 않는다. 이 장면만의
// 낱동작을 막과 함께 둔다.
@file:Suppress("MagicNumber", "TooManyFunctions")

package app.manyak.chat.room.presentation.scene

import androidx.compose.ui.geometry.Offset
import app.manyak.designsystem.component.MASCOT_HEIGHT_RATIO
import app.manyak.designsystem.component.MASCOT_WIDTH_RATIO
import app.manyak.designsystem.mascot.Act
import app.manyak.designsystem.mascot.BrushPath
import app.manyak.designsystem.mascot.Choreography
import app.manyak.designsystem.mascot.MascotEyes
import app.manyak.designsystem.mascot.MascotMoment
import app.manyak.designsystem.mascot.MascotPose
import app.manyak.designsystem.mascot.Move
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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import app.manyak.designsystem.mascot.brushTipOffset as brushTipOffsetFor

/*
 * 채팅 실시간 이미지를 기다리는 동안 마스코트가 연기하는 안무. 처음 한 번 화가 베레모를 쓰고 붓을 집어 든 뒤,
 * 4:3 가로 도화지에 밤 창가에서 달을 보는 인물 한 장을 밑그림, 밑칠, 인물, 마무리 순서로 천천히 그린다. 획은
 * 적게, 붓끝은 느리게 움직여 대화 화면에서 정신없지 않게 한다. 밑그림을 마치면 휴 하고 숨을 고르고, 인물을
 * 마치면 그림을 올려다보며 딴짓하다 번뜩이며, 물감 색을 바꿀 때마다 물그릇에 붓을 헹군다. 무대 시작부터 15초에
 * 사인을 마치고, 완성 그림을 감상한 뒤 종이를 넘겨 밑그림부터 같은 그림을 다시 그린다.
 *
 * 그림은 몸 오른쪽에 든 붓의 털 끝으로 긋는다. 붓은 몸에 고정되지 않고, 그을 때는 털 끝이 진행 반대쪽으로
 * 끌리고, 밑칠에서는 털이 눌려 퍼지고, 획을 마치면 톡 튕기고, 쉬는 막에서는 세우거나 돌린다.
 *
 * 좌표는 무대 폭을 1 로 둔 값이다(4:3 이라 높이는 3/4). 도화지 안의 그림은 도화지 폭을 84칸으로 둔 격자
 * 좌표로 적고, 붓길은 SVG path 문자열로 적어 그리는 쪽과 마스코트가 같은 길을 쓴다. 웹과 같은 값을 쓴다.
 */

internal enum class RealtimeImageAct { GEAR_UP, SKETCH, RELIEF, WASH, FIGURE, DAYDREAM, FINISH, SHOWCASE, PAGE_TURN }

/** 소품과 그림이 마스코트의 동작에 맞춰 바뀌는 순간. 그림 cue 는 그 획을 긋는 동작에 붙는다. */
internal enum class RealtimeImageCue {
    HAT,
    BRUSH,
    WINDOW,
    MULLION_V,
    MULLION_H,
    OUTLINE,
    EXHALE,
    RINSE_1,
    NIGHT,
    HAIR,
    FACE,
    EYE,
    PONDER,
    IDEA,
    RINSE_2,
    BEAM,
    CHEEK,
    SIGN,
    SHOWCASE,
    TURN,
    RINSE_3,
}

internal const val STAGE_HEIGHT = 3f / 4f
internal const val MASCOT_SIZE = 0.15f
internal const val FLOOR = STAGE_HEIGHT - 0.07f
internal const val MASCOT_HALF_WIDTH = MASCOT_SIZE * MASCOT_WIDTH_RATIO / 2f
internal const val MASCOT_HEIGHT = MASCOT_SIZE * MASCOT_HEIGHT_RATIO

internal val Home = Offset(0.2f, FLOOR)

/** 붓을 집기 전 바닥에 누운 붓의 쥔 자리. 붓털이 오른쪽을 향한다. */
internal val BrushOnFloor = Offset(0.33f, FLOOR - 0.005f)

/** 붓털 끝이 발끝 가운데에서 얼마나 떨어져 있는지. */
internal fun brushTipOffset(
    rotation: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    brush: Float = 0f,
) = brushTipOffsetFor(MASCOT_SIZE, rotation, scaleX, scaleY, brush)

// 이젤에 세운 4:3 가로 도화지. 격자는 폭 84칸, 높이 63칸이다.
internal const val PAPER_GRID = 84f
internal const val GRID_HEIGHT = 63f
internal const val PAPER_WIDTH = 0.54f
internal const val PAPER_LEFT = 0.38f
internal const val PAPER_TOP = 0.1f
internal const val PAPER_RIGHT = PAPER_LEFT + PAPER_WIDTH
internal const val PAPER_BOTTOM = PAPER_TOP + PAPER_WIDTH * GRID_HEIGHT / PAPER_GRID

/** 도화지 격자 좌표를 무대 좌표로 바꾼다. */
internal fun onPaper(at: Offset) = Offset(PAPER_LEFT, PAPER_TOP) + at * (PAPER_WIDTH / PAPER_GRID)

/** 바로 선 마스코트의 붓털 끝이 도화지 격자 [at] 에 닿는 발끝 자리. */
private fun brushAt(at: Offset): Offset = onPaper(at) - brushTipOffset()

/** 붓길의 시작점이나 끝점에 붓털 끝이 닿는 발끝 자리. */
private fun BrushPath.tip(end: Boolean = false) = brushAt(at(if (end) 1f else 0f))

/** 밑그림. 창턱을 긋고 오른쪽 창틀을 올라 아치를 넘어 왼쪽 창틀로 내려온다. */
internal val Window = parseStroke("M4,54 L47,54 L47,26 C47,15 38,9 28,9 C18,9 9,15 9,26 L9,54")

/** 창살 십자. */
internal val MullionV = parseStroke("M28,10 L28,53")
internal val MullionH = parseStroke("M10,32 L46,32")

/**
 * 창 쪽 어깨에서 턱 밑을 지나 얼굴 앞선을 타고 이마, 정수리, 뒤통수, 목덜미를 거쳐 등 쪽 어깨로 흐르는 인물
 * 윤곽. 동그란 머리와 대칭 어깨로 그리면 사람 아이콘처럼 보여 옆모습의 비대칭 선으로 잡는다.
 */
internal val Outline =
    parseStroke(
        "M53,63 C54,53 59,46 63,44 C61,40 58,36 58,30 C58,21 63,16 68,16 C74,16 76,22 75,28 C74,34 70,38 71,41 " +
            "C77,44 80,52 81,63",
    )

/** 밑칠. 창틀 안쪽이다. 밤은 이 안에만 칠한다. */
internal const val PANE = "M10,53 L10,26 C10,16 18,10 28,10 C38,10 46,16 46,26 L46,53 Z"

/** 칠하지 않고 남겨 둔 달과, 그 위를 밤 색으로 덮어 초승달로 만드는 원. */
internal val MoonAt = Offset(20f, 21f)
internal const val MOON_RADIUS = 4.2f
internal val MoonShadowAt = Offset(22.2f, 19.6f)
internal const val MOON_SHADOW_RADIUS = 3.6f

/** 창 안을 위에서 아래로 넓은 붓으로 세 번 쓸어 내리는 지그재그. */
internal val Night = parseStroke("M15,17 L41,14 L11,30 L45,28 L11,46 L45,45")

/** 인물. 이마에서 정수리를 넘어 등 뒤로 흘러내리는 머리카락. */
internal val Hair = parseStroke("M59,20 C61,14 71,13 74,19 C77,25 74,33 77,40 C79,46 77,50 80,54")

/** 창 쪽을 보는 옆얼굴의 이마, 코, 입술, 턱. */
internal val Face =
    parseStroke("M59.5,21 Q57,24.5 58,27.5 L55.5,29.5 Q57.5,30.5 57.5,31.5 Q57,33 58.5,33.5 Q59.5,37 63.5,38.5")

/** 감은 눈. */
internal val Eye = parseStroke("M59.2,25.8 Q60.8,27 62.4,26")

/** 마무리. 창 위쪽에서 인물 쪽으로 쏟아지는 달빛의 가운데 줄과, 위·아래 가장자리. */
internal val Beam = parseStroke("M40,13 L69,63")
internal val BeamTop = Offset(36f, 13f) to Offset(44f, 13f)
internal val BeamBottom = Offset(56f, 63f) to Offset(82f, 63f)
internal val CheekAt = Offset(61f, 31f)

/** 오른쪽 아래 사인. */
internal val Sign = parseStroke("M71,59.5 Q72.5,56.5 74,59 Q75.5,61 77,58.5 L79,59.8")

/** 붓을 낚아채는 순간. 점프의 이 비율에서 붓이 손에 들어온다. */
internal const val BRUSH_CATCH = 0.65f

/** 동작의 붓 회전만 바꾼다. */
private fun <C> Move<C>.brush(brush: (Float) -> Float): Move<C> =
    Move(millis, cue) { f -> pose(f).copy(brush = brush(f)) }

/** 획을 마치고 붓을 뗄 때 털 끝이 위로 톡 튀었다가 스프링처럼 제자리로 돌아오는 붓 회전. */
private fun flick(amount: Float): (Float) -> Float = { f -> -amount * exp(-5f * f) * sin(3f * PI.toFloat() * f) }

/** 숨을 내쉬듯 천천히 납작해지며 웃는 눈으로 감는다. 그동안 붓은 어깨에 기대듯 위로 세웠다가 내린다. */
private fun exhale(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val sink = smooth(f / 0.7f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f + 0.1f * sink,
        scaleY = 1f - 0.13f * sink,
        rotation = -3f * sin(PI.toFloat() * f),
        look = Offset(-0.4f, 0.2f),
        eyes = MascotEyes.SMILE,
        brush = -140f * smooth(f / 0.3f) * (1f - smooth((f - 0.7f) / 0.3f)),
    )
}

/** 납작해진 몸이 통 하고 원래대로 돌아온다. [exhale] 이 끝난 모양에서 시작한다. */
private fun perk(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val back = easeOutBack(f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1.1f - 0.1f * back,
        scaleY = 0.87f + 0.13f * back,
        eyes = if (f < 0.5f) MascotEyes.SMILE else MascotEyes.ROUND,
    )
}

/** 도화지를 올려다보며 고개를 왼쪽, 오른쪽으로 갸웃거린다. 물그릇 쪽(오른쪽)으로는 얕게 기울여 붓털이 닿지 않게 한다. */
private fun ponder(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val sway = sin(2f * PI.toFloat() * f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleY = 1f + 0.015f * sin(4f * PI.toFloat() * f),
        rotation = if (sway > 0f) -8f * sway else -3f * sway,
        look = Offset(0.6f + 0.3f * sway, -0.9f),
    )
}

// 헹굴 때 붓을 기울이는 각도와 몸이 찌그러지는 정도. 붓털 끝이 발치 물그릇에 잠긴다.
private const val RINSE_BRUSH = 60f
private const val RINSE_SCALE_X = 1.08f
private const val RINSE_SCALE_Y = 0.9f

/** 집 자리에서 몸을 낮추며 붓털 끝을 발치 물그릇에 담그고 좌우로 두 번 흔든 뒤 든다. */
private fun rinse(millis: Int) =
    Move<Nothing>(millis) { f ->
        val dip = sin(PI.toFloat() * f)
        val swish = sin(4f * PI.toFloat() * f) * dip
        MascotPose(
            x = Home.x,
            y = Home.y,
            scaleX = 1f + (RINSE_SCALE_X - 1f) * dip,
            scaleY = 1f - (1f - RINSE_SCALE_Y) * dip,
            look = Offset(0.9f, 0.9f),
            brush = RINSE_BRUSH * dip + 6f * swish,
        )
    }

// 집 자리 발치의 물그릇. 헹굴 때 붓털 끝이 닿는 자리를 가운데로 두고, 테(BOWL_RIM) 아래가 물이다.
internal val BowlX = Home.x + brushTipOffset(0f, RINSE_SCALE_X, RINSE_SCALE_Y, RINSE_BRUSH).x
internal const val BOWL_HALF_WIDTH = 0.024f
internal const val BOWL_RIM = FLOOR - 0.017f

/** 도화지 붓길을 붓털 끝으로 따라 긋는다. */
private fun trace(
    millis: Int,
    stroke: BrushPath,
    lean: Float,
    press: Float = 0f,
) = paintAlong(millis, stroke, ::onPaper, MASCOT_SIZE, lean = lean, press = press)

// 연필처럼 가는 선, 넓게 눌러 칠하는 밑칠, 굵은 붓결. 천천히 그어 끌림도 얕게 둔다.
private const val PENCIL_LEAN = 14f
private const val WASH_LEAN = 20f
private const val BRUSHY_LEAN = 18f

/** 붓길 사이를 짧게 건너뛰며 뗀 붓을 톡 튕긴다. */
private fun hop(
    millis: Int,
    from: Offset,
    to: Offset,
) = leap(millis, from, to, lift = 0.02f).brush(flick(16f))

/** 붓털 끝으로 도화지를 콕 찍는다. 몸을 낮추는 동안 붓털이 눌린다. */
private fun dab(
    millis: Int,
    at: Offset,
): Move<Nothing> {
    val move = crouch(millis, at, depth = 0.9f, look = Offset(0.7f, 0.6f))
    return Move(millis) { f -> move.pose(f).copy(press = sin(PI.toFloat() * f)) }
}

/** 완성한 그림을 반짝이는 눈으로 올려다보며 붓을 높이 들었다 내린다. */
private fun admire(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val lift = sin(PI.toFloat() * f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleX = 1f - 0.04f * lift,
        scaleY = 1f + 0.06f * lift,
        look = Offset(0.7f, -0.8f),
        eyes = MascotEyes.SPARKLE,
        brush = -150f * smooth(f / 0.3f) * (1f - smooth((f - 0.75f) / 0.25f)),
    )
}

private val Cheek = brushAt(CheekAt)
private val LookAtPaper = Offset(0.8f, -0.2f)

/** 처음 한 번만 연기하는 준비 막. 떨어지는 베레모를 머리로 받고, 발치의 붓을 뛰어올라 낚아챈다. */
private val IntroActs =
    listOf(
        Act(
            RealtimeImageAct.GEAR_UP,
            listOf(
                stand(380, Home, look = Offset(0f, -1f)).cue(RealtimeImageCue.HAT),
                crouch(150, Home, depth = 1.1f).eyes(MascotEyes.SMILE),
                stand(180, Home, look = Offset(0.8f, 0.8f)),
                crouch(110, Home, look = Offset(0.8f, 0.8f)),
                leap(360, Home, Home, lift = 0.07f).cue(RealtimeImageCue.BRUSH),
                crouch(120, Home),
            ),
        ),
    )

private val LoopActs =
    listOf(
        Act(
            RealtimeImageAct.SKETCH,
            listOf(
                stand(100, Home, look = LookAtPaper),
                crouch(140, Home),
                leap(420, Home, Window.tip(), lift = 0.045f),
                trace(1100, Window, PENCIL_LEAN).cue(RealtimeImageCue.WINDOW),
                hop(260, Window.tip(end = true), MullionV.tip()),
                trace(340, MullionV, PENCIL_LEAN).cue(RealtimeImageCue.MULLION_V),
                hop(240, MullionV.tip(end = true), MullionH.tip()),
                trace(300, MullionH, PENCIL_LEAN).cue(RealtimeImageCue.MULLION_H),
                hop(280, MullionH.tip(end = true), Outline.tip()),
                trace(720, Outline, PENCIL_LEAN).cue(RealtimeImageCue.OUTLINE),
                leap(420, Outline.tip(end = true), Home, lift = 0.04f).brush(flick(24f)),
            ),
        ),
        Act(
            RealtimeImageAct.RELIEF,
            listOf(
                crouch(150, Home, depth = 1.1f),
                exhale(560, Home).cue(RealtimeImageCue.EXHALE),
                perk(200, Home),
                rinse(460).cue(RealtimeImageCue.RINSE_1),
            ),
        ),
        Act(
            RealtimeImageAct.WASH,
            listOf(
                crouch(120, Home),
                leap(440, Home, Night.tip(), lift = 0.06f),
                trace(1140, Night, WASH_LEAN, press = 1f).cue(RealtimeImageCue.NIGHT),
            ),
        ),
        Act(
            RealtimeImageAct.FIGURE,
            listOf(
                hop(320, Night.tip(end = true), Hair.tip()),
                trace(480, Hair, BRUSHY_LEAN, press = 0.7f).cue(RealtimeImageCue.HAIR),
                hop(260, Hair.tip(end = true), Face.tip()),
                trace(330, Face, 8f).cue(RealtimeImageCue.FACE),
                hop(220, Face.tip(end = true), Eye.tip()),
                trace(220, Eye, 6f).cue(RealtimeImageCue.EYE),
                leap(440, Eye.tip(end = true), Home, lift = 0.05f).brush(flick(24f)),
                crouch(130, Home),
            ),
        ),
        Act(
            RealtimeImageAct.DAYDREAM,
            listOf(
                ponder(640, Home).cue(RealtimeImageCue.PONDER),
                stand(90, Home, look = LookAtPaper),
                // 번뜩여 뛰어오르며 붓을 손끝에서 한 바퀴 돌린다.
                leap(320, Home, Home, lift = 0.06f).brush { f -> -360f * smooth(f) }.cue(RealtimeImageCue.IDEA),
                crouch(130, Home),
                rinse(460).cue(RealtimeImageCue.RINSE_2),
            ),
        ),
        Act(
            RealtimeImageAct.FINISH,
            listOf(
                crouch(110, Home, depth = 1.1f),
                leap(440, Home, Beam.tip(), lift = 0.05f),
                trace(560, Beam, WASH_LEAN, press = 1f).cue(RealtimeImageCue.BEAM),
                hop(280, Beam.tip(end = true), Cheek),
                dab(220, Cheek).cue(RealtimeImageCue.CHEEK),
                hop(280, Cheek, Sign.tip()),
                trace(380, Sign, 12f).cue(RealtimeImageCue.SIGN),
            ),
        ),
        Act(
            RealtimeImageAct.SHOWCASE,
            listOf(
                leap(460, Sign.tip(end = true), Home, lift = 0.06f).brush(flick(30f)),
                crouch(150, Home, depth = 1.1f),
                admire(900, Home).cue(RealtimeImageCue.SHOWCASE),
            ),
        ),
        Act(
            RealtimeImageAct.PAGE_TURN,
            listOf(
                idle(760, Home, glances = listOf(Offset(0.7f, -0.9f))).cue(RealtimeImageCue.TURN),
                rinse(460).cue(RealtimeImageCue.RINSE_3),
            ),
        ),
    )

private val Intro = Choreography(IntroActs)
private val Loop = Choreography(LoopActs)
private val IntroCues = setOf(RealtimeImageCue.HAT, RealtimeImageCue.BRUSH)
private val CueAct =
    LoopActs
        .flatMap { act ->
            act.moves.mapNotNull { move -> move.cue?.let { it to act.kind } }
        }.toMap()

/** 준비 막의 길이. 이 시간이 지나면 밑그림부터 한 바퀴를 반복한다. */
internal val RealtimeImageIntroMillis = Intro.loopMillis
internal val RealtimeImageLoopMillis = Loop.loopMillis

/** 무대가 시작된 뒤의 시각에 마스코트가 어느 막에서 어떤 자세인지. 준비 막은 처음 한 번만 연기한다. */
internal fun realtimeImageMoment(millis: Int): MascotMoment<RealtimeImageAct> =
    if (millis < RealtimeImageIntroMillis) Intro.momentAt(millis) else Loop.momentAt(millis - RealtimeImageIntroMillis)

private fun owner(cue: RealtimeImageCue) = if (cue in IntroCues) Intro else Loop

/** [cue] 동작이 막 안에서 시작하는 시각. */
internal fun cueStart(cue: RealtimeImageCue) = owner(cue).cueStart(cue)

/** [cue] 동작의 길이. */
internal fun cueMillis(cue: RealtimeImageCue) = owner(cue).cueMillis(cue)

/** [cue] 동작이 막 안의 시각에 얼마나 진행했는지(0..1). */
internal fun cueProgress(
    cue: RealtimeImageCue,
    actMillis: Int,
) = owner(cue).cueProgress(cue, actMillis)

/** 반복하는 막이 한 바퀴 안에서 시작하는 시각. */
internal fun actStart(kind: RealtimeImageAct) = Loop.actStart(kind)

/**
 * 그림 [cue] 의 획을 긋는 동작이 시작된 뒤 흐른 시간. 지난 막의 획은 다 그린 것(+∞)으로 보고, 아직 오지 않은
 * 막과 준비 막에서는 시작 전(-∞)으로 본다. 한 장의 그림이 여러 막에 걸쳐 쌓이게 한다.
 */
internal fun paintedFor(
    cue: RealtimeImageCue,
    act: RealtimeImageAct,
    actMillis: Int,
): Float {
    val at = CueAct.getValue(cue).ordinal
    val now = act.ordinal
    return when {
        act == RealtimeImageAct.GEAR_UP || at > now -> Float.NEGATIVE_INFINITY
        at < now -> Float.POSITIVE_INFINITY
        else -> (actMillis - Loop.cueStart(cue)).toFloat()
    }
}

/** 붓털에 묻은 물감. */
internal enum class PaintColor { PENCIL, GRAY, GREEN }

/**
 * 붓털에 묻은 물감. 밑그림은 연필 회색, 휴에서 헹군 뒤 밑칠과 인물은 회색, 딴짓에서 헹군 뒤 마무리는 옅은
 * 초록이고, 종이를 넘기며 헹구면 연필 회색으로 돌아온다. 헹구는 동작의 한가운데를 지나면 바뀐다.
 */
internal fun paintColorAt(
    act: RealtimeImageAct,
    actMillis: Int,
): PaintColor {
    fun rinsed(cue: RealtimeImageCue) = actMillis >= Loop.cueStart(cue) + Loop.cueMillis(cue) / 2
    return when (act) {
        RealtimeImageAct.GEAR_UP, RealtimeImageAct.SKETCH -> PaintColor.PENCIL
        RealtimeImageAct.RELIEF -> if (rinsed(RealtimeImageCue.RINSE_1)) PaintColor.GRAY else PaintColor.PENCIL
        RealtimeImageAct.WASH, RealtimeImageAct.FIGURE -> PaintColor.GRAY
        RealtimeImageAct.DAYDREAM -> if (rinsed(RealtimeImageCue.RINSE_2)) PaintColor.GREEN else PaintColor.GRAY
        RealtimeImageAct.FINISH, RealtimeImageAct.SHOWCASE -> PaintColor.GREEN
        RealtimeImageAct.PAGE_TURN -> if (rinsed(RealtimeImageCue.RINSE_3)) PaintColor.PENCIL else PaintColor.GREEN
    }
}

/** 무대 시작부터 사인을 마치는 시각. */
internal val RealtimeImageFinishMillis =
    RealtimeImageIntroMillis + actStart(RealtimeImageAct.FINISH) + cueStart(RealtimeImageCue.SIGN) +
        cueMillis(RealtimeImageCue.SIGN)

/** 동작 줄이기에서 멈춰 보일 장면. 완성한 그림을 반짝이는 눈으로 올려다보는 순간이다. */
internal val RealtimeImageStillMillis =
    RealtimeImageIntroMillis + actStart(RealtimeImageAct.SHOWCASE) + cueStart(RealtimeImageCue.SHOWCASE) +
        cueMillis(RealtimeImageCue.SHOWCASE) / 2
