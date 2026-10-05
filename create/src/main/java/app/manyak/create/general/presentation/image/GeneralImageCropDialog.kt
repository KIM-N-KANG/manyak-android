package app.manyak.create.general.presentation.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.manyak.create.presentation.component.FunnelPrimaryButton
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import app.manyak.create.R as CreateR

@Composable
internal fun GeneralImageCropDialog(
    path: String,
    aspectRatio: Float,
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit,
) {
    var zoom by rememberSaveable(path) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var imageRatio by remember(path) { mutableFloatStateOf(aspectRatio) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transform = CropTransform(aspectRatio, zoom, offsetX, offsetY)
    val update: (CropTransform) -> Unit = { value ->
        val clamped = value.clampToImage(imageRatio)
        zoom = clamped.zoom
        offsetX = clamped.offsetX
        offsetY = clamped.offsetY
    }
    val save: () -> Unit = {
        working = true
        scope.launch {
            try {
                onCropped(GeneralImageCropper.crop(context, path, transform))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                failed = true
            } finally {
                working = false
            }
        }
    }
    Dialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CropDialogContent(path, transform, working, failed, update, { imageRatio = it }, onDismiss, save)
    }
}

@Composable
private fun CropDialogContent(
    path: String,
    transform: CropTransform,
    working: Boolean,
    failed: Boolean,
    update: (CropTransform) -> Unit,
    onImageRatio: (Float) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.overlay)
            .verticalScroll(rememberScrollState())
            .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            stringResource(CreateR.string.general_image_crop_title),
            style = ManyakTheme.typography.titleMedium,
            color = ManyakTheme.colors.text,
        )
        Text(
            stringResource(CreateR.string.general_image_crop_hint),
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )
        CropImagePreview(path, transform, working, update, onImageRatio)
        val zoomLabel = stringResource(CreateR.string.general_image_zoom)
        Slider(
            value = transform.zoom,
            onValueChange = { update(transform.copy(zoom = it)) },
            valueRange = 1f..5f,
            enabled = !working,
            colors =
                SliderDefaults.colors(
                    thumbColor = ManyakTheme.colors.brand,
                    activeTrackColor = ManyakTheme.colors.brand,
                ),
            modifier = Modifier.semantics { contentDescription = zoomLabel },
        )
        if (failed) GeneralCropError()
        Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            ManyakNeutralButton(stringResource(CreateR.string.general_cancel), onDismiss, Modifier.weight(1f), !working)
            FunnelPrimaryButton(
                label = stringResource(CreateR.string.general_image_crop_apply),
                enabled = !working,
                onClick = onSave,
                modifier = Modifier.weight(1f),
                loading = working,
            )
        }
    }
}

@Composable
private fun CropImagePreview(
    path: String,
    transform: CropTransform,
    working: Boolean,
    update: (CropTransform) -> Unit,
    onImageRatio: (Float) -> Unit,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val latestTransform by rememberUpdatedState(transform)
    val latestUpdate by rememberUpdatedState(update)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(transform.aspectRatio)
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(path, working) {
                if (!working) {
                    detectTransformGestures { _, pan, scale, _ ->
                        latestUpdate(
                            latestTransform.copy(
                                zoom = latestTransform.zoom * scale,
                                offsetX = latestTransform.offsetX + pan.x / viewport.width.coerceAtLeast(1),
                                offsetY = latestTransform.offsetY + pan.y / viewport.height.coerceAtLeast(1),
                            ),
                        )
                    }
                }
            },
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = stringResource(CreateR.string.general_image_crop_preview),
            modifier =
                Modifier.fillMaxWidth().aspectRatio(transform.aspectRatio).graphicsLayer {
                    scaleX = transform.zoom
                    scaleY = transform.zoom
                    translationX = transform.offsetX * viewport.width
                    translationY = transform.offsetY * viewport.height
                },
            contentScale = ContentScale.Crop,
            clipToBounds = false,
            onSuccess = {
                onImageRatio(
                    it.result.image.width
                        .toFloat() / it.result.image.height,
                )
            },
        )
    }
}

@Composable
private fun GeneralCropError() {
    Text(
        stringResource(CreateR.string.general_image_invalid),
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textDanger,
    )
}
