package app.manyak.designsystem.mascot

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MascotBrushTest {
    private val size = 0.15f
    private val line = parseStroke("M0,0 L40,0")

    private fun toStage(at: Offset) = at / 100f

    /** 붓 회전이 없고 몸이 찌그러지지 않으면 붓털 끝 자리가 이전 계산과 같아야 완성 중 표지가 바뀌지 않는다. */
    @Test
    fun brush_tip_matches_the_old_rigid_tip_without_brush_rotation() {
        val tilt = Math.toRadians(BRUSH_TILT.toDouble()).toFloat()
        val tip = Offset(BrushGrip.x - sin(tilt) * BRUSH_TIP_LENGTH, BrushGrip.y + cos(tilt) * BRUSH_TIP_LENGTH)
        for (rotation in listOf(0f, 10f, -15f)) {
            val expected = mascotPointOffset(tip, rotation, 1f, 1f, size)
            val offset = brushTipOffset(size, rotation)
            assertEquals(expected.x, offset.x, 0.00001f)
            assertEquals(expected.y, offset.y, 0.00001f)
        }
    }

    @Test
    fun brush_rotation_turns_only_the_bristles_around_the_grip() {
        val grip = mascotPointOffset(BrushGrip, 0f, 1f, 1f, size)
        val straight = brushTipOffset(size)
        val turned = brushTipOffset(size, brush = 30f)
        assertEquals((straight - grip).getDistance(), (turned - grip).getDistance(), 0.00001f)
        assertTrue(turned.x < straight.x)
        assertTrue(turned.y > straight.y)
    }

    @Test
    fun paint_along_without_options_keeps_the_brush_still() {
        val move = paintAlong(400, line, ::toStage, size)
        for (f in listOf(0f, 0.5f, 1f)) {
            assertEquals(0f, move.pose(f).brush)
            assertEquals(0f, move.pose(f).press)
        }
    }

    @Test
    fun lean_drags_the_bristles_behind_and_fades_at_the_ends() {
        val move = paintAlong(400, line, ::toStage, size, lean = 25f, press = 1f)
        assertEquals(25f, move.pose(0.5f).brush, 0.5f)
        assertEquals(1f, move.pose(0.5f).press, 0.05f)
        assertEquals(0f, move.pose(0f).brush, 0.0001f)
        assertEquals(0f, move.pose(1f).press, 0.0001f)
        val back = paintAlong(400, parseStroke("M40,0 L0,0"), ::toStage, size, lean = 25f)
        assertEquals(-25f, back.pose(0.5f).brush, 0.5f)
    }

    @Test
    fun tilted_brush_tip_stays_on_the_stroke() {
        val move = paintAlong(400, line, ::toStage, size, scribble = true, lean = 25f)
        for (f in listOf(0.2f, 0.5f, 0.8f)) {
            val pose = move.pose(f)
            val offset = brushTipOffset(size, pose.rotation, pose.scaleX, pose.scaleY, pose.brush)
            assertEquals("f=$f", 0.4f * f, pose.x + offset.x, 0.0001f)
            assertEquals("f=$f", 0f, pose.y + offset.y, 0.0001f)
        }
    }

    /** 시작 전에는 정확히 0이라 아직 나오지 않은 소품이 점으로 그려지지 않는다. */
    @Test
    fun ease_out_back_is_exactly_zero_before_it_starts() {
        assertEquals(0f, easeOutBack(0f))
        assertEquals(0f, easeOutBack(-1f))
        assertEquals(1f, easeOutBack(1f), 0.0001f)
    }
}
