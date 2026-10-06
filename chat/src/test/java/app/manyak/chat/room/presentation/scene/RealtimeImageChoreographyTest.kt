package app.manyak.chat.room.presentation.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RealtimeImageChoreographyTest {
    /** 준비 막과 한 바퀴, 그리고 다음 바퀴로 넘어가는 이음매까지 본다. */
    private val span = RealtimeImageIntroMillis + RealtimeImageLoopMillis + 1000

    /** 1ms 사이에 가장 빠른 점프도 무대 폭의 0.3% 남짓 움직인다. 그보다 훨씬 큰 차이는 이음매에서 순간이동한 것이다. */
    @Test
    fun mascot_never_teleports_including_the_intro_and_the_loop_wrap() {
        var previous = realtimeImageMoment(0).pose
        for (time in 1..span) {
            val pose = realtimeImageMoment(time).pose
            val jump = maxOf(abs(pose.x - previous.x), abs(pose.y - previous.y))
            assertTrue("t=$time jumped $jump", jump < 0.025f)
            assertTrue("t=$time spun", turn(pose.rotation, previous.rotation) < 5f)
            previous = pose
        }
    }

    @Test
    fun mascot_stays_on_the_stage() {
        for (time in 0 until span step 4) {
            val pose = realtimeImageMoment(time).pose
            val halfWidth = MASCOT_HALF_WIDTH * pose.scaleX
            assertTrue("t=$time left", pose.x - halfWidth >= -0.001f)
            assertTrue("t=$time right", pose.x + halfWidth <= 1.001f)
            assertTrue("t=$time top", pose.y - MASCOT_HEIGHT * pose.scaleY >= -0.001f)
            assertTrue("t=$time bottom", pose.y <= STAGE_HEIGHT)
        }
    }

    @Test
    fun gears_up_once_then_repeats_from_the_hype() {
        val start = realtimeImageMoment(0).pose
        assertEquals(Home.x, start.x, 0.0001f)
        assertEquals(Home.y, start.y, 0.0001f)

        val order = mutableListOf<RealtimeImageAct>()
        for (time in 0 until span step 50) {
            val act = realtimeImageMoment(time).act
            if (order.lastOrNull() != act) order += act
        }

        assertEquals(RealtimeImageAct.entries + RealtimeImageAct.HYPE, order)
    }

    @Test
    fun brush_tip_stays_on_the_stroke_while_drawing() {
        val start = RealtimeImageIntroMillis + actStart(RealtimeImageAct.SKETCH) + cueStart(RealtimeImageCue.CONTOUR)
        val millis = cueMillis(RealtimeImageCue.CONTOUR)
        for (f in listOf(0f, 0.3f, 0.6f, 1f)) {
            val time = start + (f * (millis - 1)).toInt()
            val pose = realtimeImageMoment(time).pose
            val offset = brushTipOffset(pose.rotation, pose.scaleX, pose.scaleY)
            val target = onPaper(SketchContour.at((time - start) / millis.toFloat()))
            assertEquals("f=$f x", target.x, pose.x + offset.x, 0.0001f)
            assertEquals("f=$f y", target.y, pose.y + offset.y, 0.0001f)
        }
    }

    @Test
    fun every_cue_belongs_to_a_move() {
        RealtimeImageCue.entries.forEach { cue -> assertTrue("$cue", cueMillis(cue) > 0) }
    }

    /** 실시간 이미지 응답은 약 15초 걸린다. 세 번째 그림까지 그 전에 다 그려야 기다리는 동안 안무가 끝을 맺는다. */
    @Test
    fun finishes_the_third_picture_within_fifteen_seconds() {
        val constellationDone =
            RealtimeImageIntroMillis + actStart(RealtimeImageAct.CONSTELLATION) +
                cueStart(RealtimeImageCue.CONNECT) + cueMillis(RealtimeImageCue.CONNECT)

        assertTrue(constellationDone <= 15_000)
        assertEquals(1300, RealtimeImageIntroMillis)
        assertEquals(15_250, RealtimeImageLoopMillis)
    }

    private fun turn(
        a: Float,
        b: Float,
    ): Float {
        val difference = abs(a - b) % 360f
        return minOf(difference, 360f - difference)
    }
}
