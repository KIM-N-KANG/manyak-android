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
import app.manyak.designsystem.mascot.stroll
import kotlin.math.PI
import kotlin.math.sin
import app.manyak.designsystem.mascot.brushTipOffset as brushTipOffsetFor

/*
 * 채팅 실시간 이미지를 기다리는 동안 마스코트가 연기하는 안무. 처음 한 번 화가 베레모를 쓰고 붓을 집어 든 뒤,
 * 힘을 모으고(아자아자), 붓으로 인물을 휘갈겨 그리고, 휴 하고 안도한 뒤, 수채로 다른 인물을 그리고, 딴짓하다
 * 번뜩여 뒤집은 종이에 별자리 인물을 찍는다. 다 그린 그림은 빨랫줄에 걸리고, 응답이 늦으면 아자아자부터 다시
 * 그린다. 그림은 몸 오른쪽에 든 붓의 끝으로 긋는다.
 *
 * 좌표는 무대 폭을 1 로 둔 값이다(4:3 이라 높이는 3/4). 도화지 안의 그림은 도화지 폭을 84칸으로 둔 격자
 * 좌표로 적고, 붓길은 SVG path 문자열로 적어 그리는 쪽과 마스코트가 같은 길을 쓴다. 웹과 같은 값을 쓴다.
 */

internal enum class RealtimeImageAct { GEAR_UP, HYPE, SKETCH, RELIEF, WATERCOLOR, DAYDREAM, CONSTELLATION, RESET }

/** 소품이 마스코트의 동작에 맞춰 바뀌는 순간. STAR_ 는 별자리 별을 밟아 찍는 순서다. */
internal enum class RealtimeImageCue {
    HAT,
    BRUSH,
    CHARGE,
    PUMP_1,
    PUMP_2,
    CONTOUR,
    HATCH,
    LASH,
    EXHALE,
    HALO,
    BOB,
    FACE,
    CHEEK,
    SCARF,
    PONDER,
    IDEA,
    FLIP,
    STAR_B,
    STAR_D,
    STAR_G,
    STAR_H,
    STAR_I,
    STAR_N,
    STAR_M,
    STAR_J,
    STAR_K,
    STAR_L,
    CONNECT,
}

internal const val STAGE_HEIGHT = 3f / 4f
internal const val MASCOT_SIZE = 0.15f
internal const val FLOOR = STAGE_HEIGHT - 0.07f
internal const val MASCOT_HALF_WIDTH = MASCOT_SIZE * MASCOT_WIDTH_RATIO / 2f
internal const val MASCOT_HEIGHT = MASCOT_SIZE * MASCOT_HEIGHT_RATIO

internal val Home = Offset(0.22f, FLOOR)

/** 첫 그림을 마치고 뛰어내려 숨을 고르는 자리. */
private val ReliefSpot = Offset(0.33f, FLOOR)

/** 붓을 집기 전 바닥에 누운 붓의 쥔 자리. 붓털이 오른쪽을 향한다. */
internal val BrushOnFloor = Offset(0.33f, FLOOR - 0.005f)

/** 붓털 끝이 발끝 가운데에서 얼마나 떨어져 있는지. */
internal fun brushTipOffset(
    rotation: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
) = brushTipOffsetFor(MASCOT_SIZE, rotation, scaleX, scaleY)

// 이젤에 세운 3:4 도화지. 격자 한 칸은 무대 폭의 0.005 다.
internal const val PAPER_GRID = 84f
internal const val PAPER_WIDTH = 0.42f
internal const val PAPER_LEFT = 0.45f
internal const val PAPER_TOP = 0.07f
internal const val PAPER_RIGHT = PAPER_LEFT + PAPER_WIDTH
internal const val PAPER_BOTTOM = PAPER_TOP + PAPER_WIDTH * 4f / 3f

/** 도화지 격자 좌표를 무대 좌표로 바꾼다. */
internal fun onPaper(at: Offset) = Offset(PAPER_LEFT, PAPER_TOP) + at * (PAPER_WIDTH / PAPER_GRID)

// 다 그린 그림을 거는 빨랫줄. 가운데가 SAG 만큼 처진다. 걸린 그림의 폭과 걸리는 자리·기울기다.
internal const val LINE_LEFT = 0.04f
internal const val LINE_RIGHT = 0.39f
internal const val LINE_Y = 0.115f
internal const val LINE_SAG = 0.03f
internal const val HUNG_WIDTH = 0.07f

internal class HangSlot(
    val x: Float,
    val tilt: Float,
)

internal val HangSlots = listOf(HangSlot(0.115f, -5f), HangSlot(0.215f, 3f), HangSlot(0.315f, -2f))

/** 빨랫줄 위 [x] 자리의 높이. */
internal fun clotheslineY(x: Float): Float {
    val t = (x - LINE_LEFT) / (LINE_RIGHT - LINE_LEFT)
    return LINE_Y + 4f * LINE_SAG * t * (1f - t)
}

/** 바로 선 마스코트의 붓털 끝이 도화지 격자 [at] 에 닿는 발끝 자리. */
private fun brushAt(at: Offset): Offset = onPaper(at) - brushTipOffset()

/** 붓길의 시작점이나 끝점에 붓털 끝이 닿는 발끝 자리. */
private fun BrushPath.tip(end: Boolean = false) = brushAt(at(if (end) 1f else 0f))

/** 첫 그림. 연필 한 줄로 어깨에서 목·턱·입술·코·이마를 지나 머리 뒤로 흘러내리는 긴 머리 옆얼굴이다. */
internal val SketchContour =
    parseStroke(
        "M70,96 Q60,82 48,78 L48,65.5 L52,64.5 Q58,62 56,57.5 Q58.5,55.5 56.5,53 Q58.5,51 56,48.5 L60,46 " +
            "L54,35 Q55,28 50,22 C44,12 28,12 22,24 C16,38 20,52 15,64 C11,76 18,86 13,98",
    )

/** 머리카락 한 가닥을 아래에서 위로 쓸어 올린 뒤, 머리카락 결을 지그재그로 휘갈긴다. */
internal val SketchHatch =
    parseStroke(
        "M20,94 C24,84 18,74 21,64 C24,54 20,40 26,30 L34,24 L22,34 L35,30 L21,42 L34,39 L20,50 L31,48 L19,57",
    )

/** 감은 눈의 속눈썹. */
internal val SketchLash = parseStroke("M46,40 Q49.5,42.6 53,40.2")

/** 둘째 그림. 반대쪽을 보는 단발 인물의 머리 윤곽을 굵은 붓으로 한 번에 쓴다. */
internal val WatercolorHalo = Offset(44f, 46f)
internal val WatercolorBob =
    parseStroke("M32,62 C28,63 25,60 26,54 C19,36 26,19 44,19 C60,19 69,33 64,54 C65,60 62,63 58,62")
internal val WatercolorFace = parseStroke("M31,33 Q30,40 26,45 L31,47 Q30,52 34,55 Q38,61 45,61 Q54,61 58,54")
internal val WatercolorCheek = Offset(39f, 49f)
internal val WatercolorScarf = parseStroke("M27,71 C38,64 56,64 67,70")

/** 셋째 그림의 별. 첫 별은 종이를 뒤집으며 내려앉는 자리라 FLIP 순간에 찍힌다. */
internal class Star(
    val at: Offset,
    val cue: RealtimeImageCue,
    val big: Boolean,
)

internal val Stars =
    listOf(
        Star(Offset(44f, 18f), RealtimeImageCue.FLIP, big = true),
        Star(Offset(28f, 27f), RealtimeImageCue.STAR_B, big = false),
        Star(Offset(19f, 45f), RealtimeImageCue.STAR_D, big = true),
        Star(Offset(26f, 61f), RealtimeImageCue.STAR_G, big = true),
        Star(Offset(33f, 68f), RealtimeImageCue.STAR_H, big = false),
        Star(Offset(18f, 90f), RealtimeImageCue.STAR_I, big = false),
        Star(Offset(66f, 90f), RealtimeImageCue.STAR_N, big = false),
        Star(Offset(54f, 64f), RealtimeImageCue.STAR_M, big = false),
        Star(Offset(62f, 30f), RealtimeImageCue.STAR_J, big = false),
        Star(Offset(66f, 42f), RealtimeImageCue.STAR_K, big = true),
        Star(Offset(76f, 66f), RealtimeImageCue.STAR_L, big = true),
    )

/** 별을 다 찍은 뒤 잇는 선. 옆얼굴, 머리 뒤, 포니테일 순서로 긋는다. */
internal val ConstellationLines =
    listOf(
        parseStroke(
            "M44,18 Q32,18 28,27 Q26,31 27,36 L19,45 L25,47.5 Q22.5,50 24.5,52 Q22.5,54.5 25,56 Q24,59 26,61 " +
                "Q29,65 33,68 L18,90",
        ),
        parseStroke("M44,18 Q56,19 62,30 Q65,35 66,42 Q62,55 54,64 L66,90"),
        parseStroke("M66,42 Q79,47 76,66"),
    )

/** 별자리를 이을 때 깜빡 떠오르는 눈 별. */
internal val ConstellationEye = Offset(29f, 39f)

/** 붓을 낚아채는 순간. 점프의 이 비율에서 붓이 손에 들어온다. */
internal const val BRUSH_CATCH = 0.65f

/** 바닥에 깊게 웅크려 부르르 떨며 힘을 모았다가 풀린다. 눈은 `> <` 로 힘준다. */
private fun charge(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val hold = if (f < 0.25f) smooth(f / 0.25f) else 1f - smooth((f - 0.82f) / 0.18f)
    val shiver = sin(2f * PI.toFloat() * f * millis / 45f) * 0.0035f * hold
    MascotPose(at.x + shiver, at.y, scaleX = 1f + 0.14f * hold, scaleY = 1f - 0.17f * hold, eyes = MascotEyes.FOCUS)
}

/** 숨을 내쉬듯 천천히 납작해지며 웃는 눈으로 감는다. */
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

/** 빨랫줄의 그림을 올려다보며 고개를 왼쪽, 오른쪽으로 갸웃거린다. */
private fun ponder(
    millis: Int,
    at: Offset,
) = Move<Nothing>(millis) { f ->
    val swing = sin(2f * PI.toFloat() * f)
    MascotPose(
        x = at.x,
        y = at.y,
        scaleY = 1f + 0.015f * sin(4f * PI.toFloat() * f),
        rotation = -10f * swing,
        look = Offset(-0.5f + 0.3f * swing, -0.9f),
    )
}

/** 도화지 붓길을 붓털 끝으로 따라 긋는다. */
private fun trace(
    millis: Int,
    stroke: BrushPath,
    scribble: Boolean = false,
) = paintAlong(millis, stroke, ::onPaper, MASCOT_SIZE, scribble)

/** 붓길 사이를 짧게 건너뛴다. */
private fun hop(
    millis: Int,
    from: Offset,
    to: Offset,
) = leap(millis, from, to, lift = 0.025f)

private val Halo = brushAt(WatercolorHalo)
private val Cheek = brushAt(WatercolorCheek)
private val StarFeet = Stars.map { brushAt(it.at) }
private val LookAtPaper = Offset(0.8f, -0.2f)
private val LookAtLine = Offset(-0.5f, -0.9f)

/** 별을 차례로 밟아 찍는 짧은 점프. 첫 별에서 시작해 마지막 별에 내려앉는다. */
private val StarHops =
    listOf(140, 150, 150, 120, 150, 260, 140, 170, 120, 160).mapIndexed { index, millis ->
        hop(millis, StarFeet[index], StarFeet[index + 1]).cue(Stars[index + 1].cue)
    }

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
            RealtimeImageAct.HYPE,
            listOf(
                charge(400, Home).cue(RealtimeImageCue.CHARGE),
                leap(260, Home, Home, lift = 0.05f).cue(RealtimeImageCue.PUMP_1).eyes(MascotEyes.FOCUS),
                crouch(110, Home, depth = 0.8f).eyes(MascotEyes.FOCUS),
                leap(320, Home, Home, lift = 0.09f).cue(RealtimeImageCue.PUMP_2).eyes(MascotEyes.FOCUS),
                crouch(140, Home, depth = 1.1f).eyes(MascotEyes.FOCUS),
                stand(70, Home, look = LookAtPaper),
            ),
        ),
        Act(
            RealtimeImageAct.SKETCH,
            listOf(
                stand(100, Home, look = LookAtPaper),
                crouch(130, Home),
                leap(360, Home, SketchContour.tip(), lift = 0.06f),
                trace(1220, SketchContour).cue(RealtimeImageCue.CONTOUR),
                hop(170, SketchContour.tip(end = true), SketchHatch.tip()),
                trace(760, SketchHatch, scribble = true).cue(RealtimeImageCue.HATCH),
                hop(170, SketchHatch.tip(end = true), SketchLash.tip()),
                trace(190, SketchLash).cue(RealtimeImageCue.LASH),
                leap(400, SketchLash.tip(end = true), ReliefSpot, lift = 0.05f),
            ),
        ),
        Act(
            RealtimeImageAct.RELIEF,
            listOf(
                crouch(150, ReliefSpot, depth = 1.1f),
                exhale(650, ReliefSpot).cue(RealtimeImageCue.EXHALE),
                perk(200, ReliefSpot),
                stroll(350, ReliefSpot, Home, hops = 2),
            ),
        ),
        Act(
            RealtimeImageAct.WATERCOLOR,
            listOf(
                crouch(100, Home),
                leap(460, Home, Halo, lift = 0.1f, spins = 1f),
                crouch(240, Halo, depth = 1.2f).cue(RealtimeImageCue.HALO),
                hop(150, Halo, WatercolorBob.tip()),
                trace(640, WatercolorBob).cue(RealtimeImageCue.BOB),
                hop(170, WatercolorBob.tip(end = true), WatercolorFace.tip()),
                trace(420, WatercolorFace).cue(RealtimeImageCue.FACE),
                hop(140, WatercolorFace.tip(end = true), Cheek),
                crouch(170, Cheek, depth = 0.9f).cue(RealtimeImageCue.CHEEK),
                hop(150, Cheek, WatercolorScarf.tip()),
                trace(320, WatercolorScarf).cue(RealtimeImageCue.SCARF),
                leap(420, WatercolorScarf.tip(end = true), Home, lift = 0.08f, spins = -1f),
                crouch(120, Home),
            ),
        ),
        Act(
            RealtimeImageAct.DAYDREAM,
            listOf(
                ponder(760, Home).cue(RealtimeImageCue.PONDER),
                stand(120, Home, look = LookAtLine),
                leap(280, Home, Home, lift = 0.07f).cue(RealtimeImageCue.IDEA),
                crouch(140, Home),
                stand(100, Home, look = LookAtPaper),
            ),
        ),
        Act(
            RealtimeImageAct.CONSTELLATION,
            listOf(
                stand(70, Home, look = LookAtPaper),
                crouch(100, Home, depth = 1.1f),
                leap(520, Home, StarFeet.first(), lift = 0.07f, spins = 1f).cue(RealtimeImageCue.FLIP),
            ) + StarHops +
                listOf(
                    stand(400, StarFeet.last(), look = Offset(-0.6f, 0.6f)).cue(RealtimeImageCue.CONNECT),
                    leap(420, StarFeet.last(), Home, lift = 0.06f),
                    crouch(130, Home, depth = 1.1f),
                ),
        ),
        Act(
            RealtimeImageAct.RESET,
            listOf(idle(1000, Home, glances = listOf(LookAtLine)).eyes(MascotEyes.SMILE)),
        ),
    )

private val Intro = Choreography(IntroActs)
private val Loop = Choreography(LoopActs)
private val IntroCues = setOf(RealtimeImageCue.HAT, RealtimeImageCue.BRUSH)

/** 준비 막의 길이. 이 시간이 지나면 아자아자부터 한 바퀴를 반복한다. */
internal val RealtimeImageIntroMillis = Intro.loopMillis
internal val RealtimeImageLoopMillis = Loop.loopMillis

/** 무대가 시작된 뒤의 시각에 마스코트가 어느 막에서 어떤 자세인지. 준비 막은 처음 한 번만 연기한다. */
internal fun realtimeImageMoment(millis: Int): MascotMoment<RealtimeImageAct> =
    if (millis < RealtimeImageIntroMillis) Intro.momentAt(millis) else Loop.momentAt(millis - RealtimeImageIntroMillis)

/** 반복하는 한 바퀴 안의 시각. 준비 막 동안에는 -1 이다. */
internal fun loopTime(millis: Int): Int =
    if (millis < RealtimeImageIntroMillis) -1 else (millis - RealtimeImageIntroMillis) % RealtimeImageLoopMillis

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

/** 동작 줄이기에서 멈춰 보일 장면. 크로키를 마치고 도화지 옆에 내려선 순간이다. */
internal val RealtimeImageStillMillis = RealtimeImageIntroMillis + actStart(RealtimeImageAct.RELIEF) + 150
