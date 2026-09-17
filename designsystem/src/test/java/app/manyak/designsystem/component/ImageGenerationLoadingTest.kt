package app.manyak.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationLoadingTest {
    @Test
    fun dotsMoveAndGrowSmoothlyOnlyInsideTheHighlight() {
        assertEquals(1f, generationDotInfluence(0f, 100f), 0f)
        assertEquals(0.5f, generationDotInfluence(50f, 100f), 0.0001f)
        assertEquals(0f, generationDotInfluence(100f, 100f), 0f)
        assertEquals(0f, generationDotInfluence(200f, 100f), 0f)
        assertEquals(0f, generationDotInfluence(0f, 0f), 0f)
        var previous = 1f
        for (distance in 0..100) {
            val influence = generationDotInfluence(distance.toFloat(), 100f)
            assertTrue(influence in 0f..previous)
            previous = influence
        }
        assertTrue(generationDotInfluence(99.9f, 100f) < 0.00001f)
        assertTrue(1f - generationDotInfluence(0.1f, 100f) < 0.00001f)
    }
}
