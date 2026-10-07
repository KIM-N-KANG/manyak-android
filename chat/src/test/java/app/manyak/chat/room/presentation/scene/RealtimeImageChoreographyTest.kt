package app.manyak.chat.room.presentation.scene

import androidx.compose.ui.geometry.Offset
import app.manyak.designsystem.mascot.BrushPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RealtimeImageChoreographyTest {
    /** 준비 막과 한 바퀴, 그리고 다음 바퀴로 넘어가는 이음매까지 본다. */
    private val span = RealtimeImageIntroMillis + RealtimeImageLoopMillis + 1000

    /** 붓길 cue 와 그 막, 붓길. */
    private val strokes: List<Triple<RealtimeImageCue, RealtimeImageAct, BrushPath>> =
        listOf(
            Triple(RealtimeImageCue.WINDOW, RealtimeImageAct.SKETCH, Window),
            Triple(RealtimeImageCue.MULLION_V, RealtimeImageAct.SKETCH, MullionV),
            Triple(RealtimeImageCue.MULLION_H, RealtimeImageAct.SKETCH, MullionH),
            Triple(RealtimeImageCue.OUTLINE, RealtimeImageAct.SKETCH, Outline),
            Triple(RealtimeImageCue.NIGHT, RealtimeImageAct.WASH, Night),
            Triple(RealtimeImageCue.HAIR, RealtimeImageAct.FIGURE, Hair),
            Triple(RealtimeImageCue.FACE, RealtimeImageAct.FIGURE, Face),
            Triple(RealtimeImageCue.EYE, RealtimeImageAct.FIGURE, Eye),
            Triple(RealtimeImageCue.BEAM, RealtimeImageAct.FINISH, Beam),
            Triple(RealtimeImageCue.SIGN, RealtimeImageAct.FINISH, Sign),
        )

    private fun startOf(
        act: RealtimeImageAct,
        cue: RealtimeImageCue,
    ) = RealtimeImageIntroMillis + actStart(act) + cueStart(cue)

    private fun tipAt(time: Int) =
        realtimeImageMoment(time).pose.let { pose ->
            Offset(pose.x, pose.y) +
                brushTipOffset(pose.rotation, pose.scaleX, pose.scaleY, pose.brush)
        }

    /**
     * 1ms 사이에 가장 빠른 점프도 무대 폭의 0.3% 남짓 움직인다. 그보다 훨씬 큰 차이는 이음매에서 순간이동한 것이다.
     * 붓 회전과 누름도 같은 이음매에서 튀지 않아야 한다.
     */
    @Test
    fun mascot_and_brush_never_teleport_including_the_intro_and_the_loop_wrap() {
        var previous = realtimeImageMoment(0).pose
        for (time in 1..span) {
            val pose = realtimeImageMoment(time).pose
            val jump = maxOf(abs(pose.x - previous.x), abs(pose.y - previous.y))
            assertTrue("t=$time jumped $jump", jump < 0.025f)
            assertTrue("t=$time spun", turn(pose.rotation, previous.rotation) < 5f)
            assertTrue("t=$time brush", turn(pose.brush, previous.brush) < 5f)
            assertTrue("t=$time press", abs(pose.press - previous.press) < 0.2f)
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
    fun gears_up_once_then_repeats_from_the_sketch_to_the_page_turn() {
        val start = realtimeImageMoment(0).pose
        assertEquals(Home.x, start.x, 0.0001f)
        assertEquals(Home.y, start.y, 0.0001f)

        val order = mutableListOf<RealtimeImageAct>()
        for (time in 0 until span step 50) {
            val act = realtimeImageMoment(time).act
            if (order.lastOrNull() != act) order += act
        }

        assertEquals(RealtimeImageAct.entries + RealtimeImageAct.SKETCH, order)
    }

    @Test
    fun tilted_brush_tip_stays_on_the_stroke_while_drawing() {
        for ((cue, act, stroke) in strokes) {
            val start = startOf(act, cue)
            val millis = cueMillis(cue)
            for (f in listOf(0f, 0.3f, 0.6f, 1f)) {
                val time = start + (f * (millis - 1)).toInt()
                val target = onPaper(stroke.at((time - start) / millis.toFloat()))
                val tip = tipAt(time)
                assertEquals("$cue f=$f x", target.x, tip.x, 0.0001f)
                assertEquals("$cue f=$f y", target.y, tip.y, 0.0001f)
            }
        }
    }

    /** 정신없지 않게 붓끝이 천천히 움직여야 한다. 붓길 길이(격자 칸)를 긋는 시간으로 나눈 평균 속도를 본다. */
    @Test
    fun every_stroke_is_drawn_slower_than_160_grid_cells_per_second() {
        for ((cue, _, stroke) in strokes) assertTrue("$cue", stroke.total / cueMillis(cue) * 1000f < 160f)
    }

    @Test
    fun bristles_drag_while_drawing_and_press_while_washing() {
        val window = realtimeImageMoment(startOf(RealtimeImageAct.SKETCH, RealtimeImageCue.WINDOW) + 165).pose
        val night =
            realtimeImageMoment(
                startOf(RealtimeImageAct.WASH, RealtimeImageCue.NIGHT) + cueMillis(RealtimeImageCue.NIGHT) / 2,
            ).pose
        assertTrue(abs(window.brush) > 5f)
        assertTrue(night.press > 0.5f)
    }

    /** 붓털 끝 옆의 붓털이 물그릇 몸통에 가려지지 않도록 붓털 두께만큼 넓게 본다. */
    @Test
    fun brush_dips_into_the_bowl_only_while_rinsing() {
        val rinses =
            listOf(
                RealtimeImageAct.RELIEF to RealtimeImageCue.RINSE_1,
                RealtimeImageAct.DAYDREAM to RealtimeImageCue.RINSE_2,
                RealtimeImageAct.PAGE_TURN to RealtimeImageCue.RINSE_3,
            ).map { (act, cue) -> actStart(act) + cueStart(cue) until actStart(act) + cueStart(cue) + cueMillis(cue) }
        var dipped = 0
        for (time in 0 until span step 2) {
            val tip = tipAt(time)
            val inBowl = abs(tip.x - BowlX) < BOWL_HALF_WIDTH + 0.004f && tip.y > BOWL_RIM
            if (!inBowl) continue
            dipped++
            val lap = (time - RealtimeImageIntroMillis) % RealtimeImageLoopMillis
            assertTrue("t=$time", time >= RealtimeImageIntroMillis && rinses.any { lap in it })
        }
        assertTrue(dipped > 0)
    }

    @Test
    fun every_cue_belongs_to_a_move() {
        RealtimeImageCue.entries.forEach { cue -> assertTrue("$cue", cueMillis(cue) > 0) }
    }

    /** 실시간 이미지 응답은 약 15초 걸린다. 준비 막을 포함해 그 순간에 사인을 마쳐 그림 한 장이 끝을 맺는다. */
    @Test
    fun signs_at_fifteen_seconds_and_loops_in_a_bit_over_sixteen() {
        val signed = startOf(RealtimeImageAct.FINISH, RealtimeImageCue.SIGN) + cueMillis(RealtimeImageCue.SIGN)
        assertEquals(signed, RealtimeImageFinishMillis)
        assertTrue(abs(signed - 15_000) <= 50)
        assertEquals(1300, RealtimeImageIntroMillis)
        assertTrue(RealtimeImageLoopMillis in 16_000..17_000)
    }

    @Test
    fun earlier_layers_stay_and_later_layers_wait() {
        assertEquals(Float.NEGATIVE_INFINITY, paintedFor(RealtimeImageCue.WINDOW, RealtimeImageAct.GEAR_UP, 500))
        assertEquals(Float.NEGATIVE_INFINITY, paintedFor(RealtimeImageCue.NIGHT, RealtimeImageAct.SKETCH, 500))
        assertEquals(Float.POSITIVE_INFINITY, paintedFor(RealtimeImageCue.NIGHT, RealtimeImageAct.FIGURE, 0))
        assertEquals(Float.POSITIVE_INFINITY, paintedFor(RealtimeImageCue.SIGN, RealtimeImageAct.PAGE_TURN, 0))
        assertEquals(
            40f,
            paintedFor(RealtimeImageCue.HAIR, RealtimeImageAct.FIGURE, cueStart(RealtimeImageCue.HAIR) + 40),
            0.0001f,
        )
    }

    @Test
    fun paint_changes_at_each_rinse_and_returns_to_pencil_after_the_page_turn() {
        assertEquals(PaintColor.PENCIL, paintColorAt(RealtimeImageAct.SKETCH, 0))
        assertEquals(PaintColor.PENCIL, paintColorAt(RealtimeImageAct.RELIEF, 0))
        assertEquals(
            PaintColor.GRAY,
            paintColorAt(
                RealtimeImageAct.RELIEF,
                cueStart(RealtimeImageCue.RINSE_1) + cueMillis(RealtimeImageCue.RINSE_1),
            ),
        )
        assertEquals(PaintColor.GRAY, paintColorAt(RealtimeImageAct.FIGURE, 0))
        assertEquals(PaintColor.GREEN, paintColorAt(RealtimeImageAct.FINISH, 0))
        assertEquals(
            PaintColor.PENCIL,
            paintColorAt(
                RealtimeImageAct.PAGE_TURN,
                cueStart(RealtimeImageCue.RINSE_3) + cueMillis(RealtimeImageCue.RINSE_3),
            ),
        )
        assertEquals(PaintColor.PENCIL, paintColorAt(RealtimeImageAct.GEAR_UP, 0))
    }

    private fun turn(
        a: Float,
        b: Float,
    ): Float {
        val difference = abs(a - b) % 360f
        return minOf(difference, 360f - difference)
    }
}
