package app.manyak.designsystem.mascot

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class BrushPathTest {
    @Test
    fun follows_the_path_evenly_by_length() {
        val path = parseStroke("M0,0 L3,4 L3,10")

        assertEquals(11f, path.total, 0.0001f)
        assertEquals(Offset(0f, 0f), path.at(0f))
        assertEquals(4f, path.at(5f / 11f).y, 0.0001f)
        assertEquals(Offset(3f, 10f), path.at(1f))
    }

    @Test
    fun splits_curves_into_short_segments_that_end_on_the_curve() {
        val path = parseStroke("M0,0 Q5,10 10,0 C12,4 14,4 16,0")

        assertEquals(1 + 14 + 14, path.points.size)
        assertEquals(Offset(16f, 0f), path.at(1f))
    }
}
