package app.manyak.create.general.presentation.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.manyak.create.presentation.component.FunnelPrimaryButton
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import app.manyak.create.R as CreateR

/**
 * 고른 이미지를 정해진 비율로 자르는 시트. 검은 판 위에 자르기 창을 두고 창 밖은 어둡게 남겨 잘려 나갈
 * 부분도 보이게 한다. 이동과 확대는 창 크기를 기준으로 재므로 판 크기가 바뀌어도 고른 범위가 같다.
 */
@Composable
internal fun GeneralImageCropSheet(
    path: String,
    aspectRatio: Float,
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit,
) {
    var zoom by rememberSaveable(path) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var imageRatio by remember(path) { mutableStateOf<Float?>(null) }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transform = CropTransform(aspectRatio, zoom, offsetX, offsetY)
    val update: (CropTransform) -> Unit = { value ->
        val clamped = value.clampToImage(imageRatio ?: aspectRatio)
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
    // 웹처럼 화면에는 제목을 두지 않고 보조 기술에만 시트 이름을 알린다.
    val title = stringResource(CreateR.string.general_image_crop_title)
    ManyakBottomSheet(
        modifier = Modifier.semantics { paneTitle = title },
        onDismissRequest = { if (!working) onDismiss() },
        dismissEnabled = !working,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        CropArea(path, transform, imageRatio, working, update) { imageRatio = it }
        ZoomSlider(transform, working, update)
        if (failed) {
            Text(
                stringResource(CreateR.string.general_image_invalid),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textDanger,
            )
        }
        CropSheetActions(imageRatio != null, working, save, onDismiss)
    }
}

@Composable
private fun CropSheetActions(
    ready: Boolean,
    working: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        FunnelPrimaryButton(
            label = stringResource(CreateR.string.general_image_crop_apply),
            enabled = ready,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            loading = working,
        )
        ManyakTextButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
            enabled = !working,
            onClick = onDismiss,
        ) {
            Text(
                stringResource(CreateR.string.general_image_close),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/** 시트 좌우 여백을 넘어 판을 화면 폭 끝까지 넓힌다. */
private fun Modifier.fullBleed(gutter: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = gutter.roundToPx() * 2
        val width = constraints.maxWidth + extra
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }

@Composable
private fun CropArea(
    path: String,
    transform: CropTransform,
    imageRatio: Float?,
    working: Boolean,
    update: (CropTransform) -> Unit,
    onImageRatio: (Float) -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * CROP_AREA_MAX_HEIGHT_FRACTION
    val latestTransform by rememberUpdatedState(transform)
    val latestUpdate by rememberUpdatedState(update)
    val backdrop = ManyakTheme.colors.imageViewerScrim
    BoxWithConstraints(Modifier.padding(top = ManyakTheme.spacing.component).fullBleed(ManyakTheme.spacing.gutter)) {
        val area = DpSize(maxWidth, minOf(maxWidth, maxHeight))
        val window = cropWindowSize(area, imageRatio ?: transform.aspectRatio, transform.aspectRatio)
        val windowPx = with(LocalDensity.current) { Size(window.width.toPx(), window.height.toPx()) }
        Box(
            Modifier
                .size(area)
                .background(backdrop)
                .clipToBounds()
                .pointerInput(path, working, windowPx) {
                    if (!working) {
                        detectTransformGestures { _, pan, scale, _ ->
                            latestUpdate(
                                latestTransform.copy(
                                    zoom = latestTransform.zoom * scale,
                                    offsetX = latestTransform.offsetX + pan.x / windowPx.width.coerceAtLeast(1f),
                                    offsetY = latestTransform.offsetY + pan.y / windowPx.height.coerceAtLeast(1f),
                                ),
                            )
                        }
                    }
                }.drawWithContent {
                    drawContent()
                    drawCropWindow(windowPx, backdrop)
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(CreateR.string.general_image_crop_preview),
                modifier =
                    Modifier.size(window).graphicsLayer {
                        scaleX = transform.zoom
                        scaleY = transform.zoom
                        translationX = transform.offsetX * windowPx.width
                        translationY = transform.offsetY * windowPx.height
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
}

/** 잘릴 창 밖을 어둡게 덮고 창에 테두리와 3×3 격자를 그린다. */
private fun DrawScope.drawCropWindow(
    window: Size,
    backdrop: Color,
) {
    val rect = Rect(Offset((size.width - window.width) / 2f, (size.height - window.height) / 2f), window)
    clipRect(rect.left, rect.top, rect.right, rect.bottom, ClipOp.Difference) {
        drawRect(backdrop.copy(alpha = OUTSIDE_SCRIM_ALPHA))
    }
    val line = GridLineColor
    val stroke = 1.dp.toPx()
    for (i in 1 until GRID_DIVISIONS) {
        val x = rect.left + rect.width * i / GRID_DIVISIONS
        val y = rect.top + rect.height * i / GRID_DIVISIONS
        drawLine(line, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = stroke)
        drawLine(line, Offset(rect.left, y), Offset(rect.right, y), strokeWidth = stroke)
    }
    drawRect(line, rect.topLeft, rect.size, style = Stroke(stroke))
}

/** 확대 슬라이더. 얇은 트랙과 둥근 손잡이로 보조 조절임을 드러내고 주 색은 채운 구간에만 쓴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoomSlider(
    transform: CropTransform,
    working: Boolean,
    update: (CropTransform) -> Unit,
) {
    val zoomLabel = stringResource(CreateR.string.general_image_zoom)
    val brand = ManyakTheme.colors.brand
    val rest = ManyakTheme.colors.border
    Slider(
        value = transform.zoom,
        onValueChange = { update(transform.copy(zoom = it)) },
        valueRange = 1f..MAX_CROP_ZOOM,
        enabled = !working,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = zoomLabel },
        thumb = { Box(Modifier.size(ManyakTheme.sizes.icon).background(brand, ManyakTheme.shapes.pill)) },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(ManyakTheme.spacing.inline)) {
                val y = size.height / 2f
                val end = size.width * state.coercedValueAsFraction
                drawLine(rest, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                drawLine(brand, Offset(0f, y), Offset(end, y), size.height, StrokeCap.Round)
            }
        },
    )
}

/**
 * 판 안에서 이미지를 통째로 보이게 놓았을 때 그 이미지 안에 들어가는 가장 큰 자르기 창. 창이 이미지 밖으로
 * 나가지 않으므로 확대하지 않은 상태가 곧 이미지를 가장 넓게 담는 범위다.
 */
internal fun cropWindowSize(
    area: DpSize,
    imageRatio: Float,
    cropRatio: Float,
): DpSize {
    val areaRatio = area.width / area.height
    val image =
        if (imageRatio > areaRatio) {
            DpSize(area.width, area.width / imageRatio)
        } else {
            DpSize(area.height * imageRatio, area.height)
        }
    return if (cropRatio > image.width / image.height) {
        DpSize(image.width, image.width / cropRatio)
    } else {
        DpSize(image.height * cropRatio, image.height)
    }
}

private const val CROP_AREA_MAX_HEIGHT_FRACTION = 0.6f
private const val OUTSIDE_SCRIM_ALPHA = 0.5f
private const val GRID_LINE_ALPHA = 0.5f
private const val GRID_DIVISIONS = 3

// 검은 판 위에서 테마와 관계없이 보여야 하는 선이라 밝은 색으로 고정한다.
private val GridLineColor = Color.White.copy(alpha = 0.5f)
