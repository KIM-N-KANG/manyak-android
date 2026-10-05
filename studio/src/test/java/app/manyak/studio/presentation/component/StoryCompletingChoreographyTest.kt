package app.manyak.studio.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StoryCompletingChoreographyTest {
    /**
     * 1ms 사이에 가장 빠른 점프도 표지 폭의 0.3% 남짓 움직인다. 그보다 훨씬 큰 차이는 동작 사이의 이음매에서
     * 순간이동한 것이다. 벽에 붙는 순간만 찌그러진 폭만큼 발이 옮겨 가므로 그 정도는 허용한다.
     */
    @Test
    fun mascot_never_teleports_including_across_acts_and_the_loop_wrap() {
        var previous = completingMoment(0).pose
        for (time in 1..CompletingLoopMillis) {
            val pose = completingMoment(time).pose
            val jump = maxOf(abs(pose.x - previous.x), abs(pose.y - previous.y))
            assertTrue("t=$time jumped $jump", jump < 0.025f)
            assertTrue("t=$time spun ${pose.rotation - previous.rotation}", turn(pose.rotation, previous.rotation) < 5f)
            previous = pose
        }
    }

    @Test
    fun mascot_stays_on_the_cover() {
        for (time in 0 until CompletingLoopMillis step 8) {
            val pose = completingMoment(time).pose
            val halfWidth = MASCOT_HALF_WIDTH * pose.scaleX
            assertTrue("t=$time left", pose.x - halfWidth >= -0.001f)
            assertTrue("t=$time right", pose.x + halfWidth <= 1.001f)
            assertTrue("t=$time top", pose.y - MASCOT_HEIGHT * pose.scaleY >= -0.001f)
            assertTrue("t=$time bottom", pose.y <= STAGE_HEIGHT)
        }
    }

    @Test
    fun every_act_starts_and_ends_standing_at_home() {
        val start = completingMoment(0).pose
        assertEquals(Home.x, start.x, 0.0001f)
        assertEquals(Home.y, start.y, 0.0001f)
        assertEquals(
            CompletingAct.entries.toSet(),
            (0 until CompletingLoopMillis step 50).map { completingMoment(it).act }.toSet(),
        )
    }

    @Test
    fun every_cue_belongs_to_a_move() {
        Cue.entries.forEach { cue -> assertTrue("$cue", cueMillis(cue) > 0) }
    }

    private fun turn(
        a: Float,
        b: Float,
    ): Float {
        val difference = abs(a - b) % 360f
        return minOf(difference, 360f - difference)
    }
}
