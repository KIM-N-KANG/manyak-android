package app.manyak.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenImageViewerTest {
    private val viewport = Size(100f, 200f)
    private val square = Size(100f, 100f)

    @Test
    fun letterboxIsNotTheImage() {
        assertTrue(isOnFittedImage(Offset(50f, 100f), viewport, square, 1f, Offset.Zero))
        assertFalse(isOnFittedImage(Offset(50f, 20f), viewport, square, 1f, Offset.Zero))
        assertFalse(isOnFittedImage(Offset(50f, 100f), viewport, Size.Unspecified, 1f, Offset.Zero))
    }

    @Test
    fun followsZoomAndPan() {
        assertTrue(isOnFittedImage(Offset(50f, 20f), viewport, square, 2f, Offset.Zero))
        assertFalse(isOnFittedImage(Offset(50f, 50f), viewport, square, 2f, Offset(0f, 100f)))
    }
}
