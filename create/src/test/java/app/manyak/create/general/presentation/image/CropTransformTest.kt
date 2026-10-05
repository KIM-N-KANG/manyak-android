package app.manyak.create.general.presentation.image

import org.junit.Assert.assertEquals
import org.junit.Test

class CropTransformTest {
    @Test
    fun squareImageInPortraitFrameCanPanOnlyHorizontallyAtMinimumZoom() {
        val result = CropTransform(3f / 4f, 1f, 3f, 3f).clampToImage(1f)
        assertEquals(1f / 6f, result.offsetX, 0.0001f)
        assertEquals(0f, result.offsetY, 0.0001f)
    }

    @Test
    fun squareImageInLandscapeFrameCanPanOnlyVerticallyAtMinimumZoom() {
        val result = CropTransform(4f / 3f, 1f, -3f, -3f).clampToImage(1f)
        assertEquals(0f, result.offsetX, 0.0001f)
        assertEquals(-1f / 6f, result.offsetY, 0.0001f)
    }

    @Test
    fun zoomingBackOutClampsBothAxesAndCannotRevealOutsideTheImage() {
        val result = CropTransform(4f / 3f, 0.5f, 2f, -2f).clampToImage(4f / 3f)
        assertEquals(1f, result.zoom, 0.0001f)
        assertEquals(0f, result.offsetX, 0.0001f)
        assertEquals(0f, result.offsetY, 0.0001f)
    }

    @Test
    fun zoomCannotExceedMaximumAndOffsetsRemainInsideVisibleImage() {
        val result = CropTransform(3f / 4f, 10f, 10f, -10f).clampToImage(3f / 4f)
        assertEquals(5f, result.zoom, 0.0001f)
        assertEquals(2f, result.offsetX, 0.0001f)
        assertEquals(-2f, result.offsetY, 0.0001f)
    }

    @Test
    fun oddSizedPortraitOutputKeepsExactThreeToFourRatio() {
        val size = cropOutputSize(511, 681, 3f / 4f)
        assertEquals(510 to 680, size)
    }

    @Test
    fun largeLandscapeOutputIsExactlyFourToThreeAndAtMost1440Pixels() {
        val size = cropOutputSize(4000, 3000, 4f / 3f)
        assertEquals(1440 to 1080, size)
    }

    @Test
    fun tinySourceStillProducesValidExactRatioDimensions() {
        assertEquals(3 to 4, cropOutputSize(1, 1, 3f / 4f))
    }
}
