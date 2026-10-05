package app.manyak.create.general.presentation.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal object GeneralImageCropper {
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_DECODE_SIDE = 2048
    private const val JPEG_QUALITY = 90
    private val allowedTypes = setOf("image/jpeg", "image/png", "image/webp")

    suspend fun prepare(
        context: Context,
        uri: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            val bytes = readImageBytes(context, uri)
            val bounds = imageBounds(bytes)
            val orientation =
                try {
                    ByteArrayInputStream(bytes).use { stream ->
                        ExifInterface(
                            stream,
                        ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                } catch (_: IOException) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (max(bounds.outWidth, bounds.outHeight) / inSampleSize > MAX_DECODE_SIDE) inSampleSize *= 2
                }
            val bitmap =
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw IOException("image_unreadable")
            val matrix = orientationMatrix(orientation)
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            try {
                writeCache(context, upright)
            } finally {
                if (upright !== bitmap) upright.recycle()
                bitmap.recycle()
            }
        }

    suspend fun crop(
        context: Context,
        path: String,
        crop: CropTransform,
    ): String =
        withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(path) ?: throw IOException("image_unreadable")
            try {
                val baseWidth = min(bitmap.width.toFloat(), bitmap.height * crop.aspectRatio)
                val width = (baseWidth / crop.zoom).roundToInt().coerceIn(1, bitmap.width)
                val height = (width / crop.aspectRatio).roundToInt().coerceIn(1, bitmap.height)
                val left =
                    ((bitmap.width - width) / 2f - crop.offsetX * width)
                        .roundToInt()
                        .coerceIn(0, bitmap.width - width)
                val top =
                    ((bitmap.height - height) / 2f - crop.offsetY * height)
                        .roundToInt()
                        .coerceIn(0, bitmap.height - height)
                val region = Bitmap.createBitmap(bitmap, left, top, width, height)
                val (outputWidth, outputHeight) = cropOutputSize(width, height, crop.aspectRatio)
                val scaled =
                    Bitmap.createScaledBitmap(
                        region,
                        outputWidth,
                        outputHeight,
                        true,
                    )
                try {
                    writeCache(context, scaled)
                } finally {
                    if (scaled !== region) scaled.recycle()
                    if (region !== bitmap) region.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }

    private fun readImageBytes(
        context: Context,
        uri: Uri,
    ): ByteArray {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = input.read(buffer)
                while (count != -1) {
                    if (output.size() + count > MAX_BYTES) throw IOException("image_too_large")
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                output.toByteArray()
            } ?: throw IOException("image_unreadable")
        return bytes
    }

    private fun imageBounds(bytes: ByteArray): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
            if (outMimeType !in allowedTypes || outWidth <= 0 || outHeight <= 0) {
                throw IOException("image_invalid")
            }
        }

    private fun writeCache(
        context: Context,
        bitmap: Bitmap,
    ): String {
        val directory = File(context.cacheDir, "general-story-images").apply { mkdirs() }
        val output = File.createTempFile("crop-", ".jpg", directory)
        val encoded = output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (!encoded) {
            output.delete()
            throw IOException("image_unreadable")
        }
        if (output.length() > MAX_BYTES) {
            output.delete()
            throw IOException("image_too_large")
        }
        return output.absolutePath
    }

    @Suppress("MagicNumber")
    private fun orientationMatrix(orientation: Int): Matrix =
        Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
}

internal data class CropTransform(
    val aspectRatio: Float,
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
)

internal fun CropTransform.clampToImage(imageRatio: Float): CropTransform {
    val scale = zoom.coerceIn(1f, MAX_CROP_ZOOM)
    val widthScale = max(1f, imageRatio / aspectRatio) * scale
    val heightScale = max(1f, aspectRatio / imageRatio) * scale
    return copy(
        zoom = scale,
        offsetX = offsetX.coerceIn(-(widthScale - 1f) / 2f, (widthScale - 1f) / 2f),
        offsetY = offsetY.coerceIn(-(heightScale - 1f) / 2f, (heightScale - 1f) / 2f),
    )
}

private const val MAX_CROP_ZOOM = 5f
