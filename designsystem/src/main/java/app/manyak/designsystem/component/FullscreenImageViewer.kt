package app.manyak.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.times
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage
import app.manyak.designsystem.R as DesignsystemR

/** 화면의 일시 오버레이. 열린 이미지와 닫기는 호출 화면이, 확대·이동은 뷰어가 소유한다. */
@Composable
fun FullscreenImageViewer(
    imageUrl: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrl == null) return
    BackHandler(onBack = onClose)
    LightSystemBarIcons()

    val closeLabel = stringResource(DesignsystemR.string.image_viewer_close)
    val currentOnClose by rememberUpdatedState(onClose)
    val zoom = remember(imageUrl) { ZoomState() }
    val transformState =
        rememberTransformableState { zoomChange, panChange, _ ->
            zoom.transform(zoomChange, panChange)
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ManyakTheme.colors.imageViewerScrim)
                .semantics {
                    onClick(label = closeLabel) {
                        onClose()
                        true
                    }
                }.pointerInput(imageUrl) {
                    detectTapGestures(
                        // 그림 밖 배경을 누를 때만 닫힌다. 그림 위 탭은 확대하려다 닿은 손일 수 있다.
                        onTap = { tap -> if (!zoom.isOnImage(tap)) currentOnClose() },
                        onDoubleTap = { zoom.toggle(tap = it, viewportCenter = size.center) },
                    )
                }.transformable(transformState),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { zoom.viewport = it }
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
            model = imageUrl,
            onSuccess = { success -> zoom.imageSize = success.painter.intrinsicSize },
            // 원본을 잘라 보여 주려고 여는 자리가 아니라 전체를 보려고 여는 자리다.
            contentScale = ContentScale.Fit,
            // 스토리 제목·소개가 이미 말하는 것을 되풀이하지 않는다. 낭독 대상은 닫기 버튼이다.
            contentDescription = null,
        )
        ManyakIconButton(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(ManyakTheme.spacing.compact),
            iconRes = DesignsystemR.drawable.ic_close,
            contentDescription = closeLabel,
            onClick = onClose,
            // 어떤 이미지 위에 놓일지 알 수 없어 색은 테마가 아니라 어두운 바탕 대비로 정한다.
            tint = ManyakTheme.colors.textInverse,
        )
    }
}

/** 어두운 이미지 위에서 상태 바 아이콘을 밝히고 닫을 때 원래 값으로 되돌린다. */
@Composable
fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = ViewCompat.getWindowInsetsController(view)
        val wasLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            wasLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
        }
    }
}

/** 확대 배율과 이동량. 이동은 뷰포트 밖으로 이미지가 빠져나가지 않는 범위로만 허용한다. */
private class ZoomState {
    var scale by mutableFloatStateOf(MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport = IntSize.Zero

    /** 불러온 그림의 원본 크기. 불러오기 전에는 그림이 없는 것으로 본다. */
    var imageSize = Size.Unspecified

    fun isOnImage(tap: Offset): Boolean = isOnFittedImage(tap, viewport.toSize(), imageSize, scale, offset)

    fun transform(
        zoomChange: Float,
        panChange: Offset,
    ) {
        scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        offset = clamp(offset + panChange)
    }

    fun toggle(
        tap: Offset,
        viewportCenter: Offset,
    ) {
        if (scale > MIN_SCALE) {
            scale = MIN_SCALE
            offset = Offset.Zero
        } else {
            scale = DOUBLE_TAP_SCALE
            // 누른 지점이 확대 뒤에도 같은 자리에 오도록 그만큼 밀어 둔다.
            offset = clamp((viewportCenter - tap) * (DOUBLE_TAP_SCALE - 1f))
        }
    }

    private fun clamp(candidate: Offset): Offset {
        val maxX = viewport.width * (scale - 1f) / 2f
        val maxY = viewport.height * (scale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }
}

/**
 * [tap]이 [viewport]에 Fit으로 맞춘 뒤 가운데 기준 [scale]배 확대하고 [offset]만큼 옮겨 그린 그림 위인지.
 * 여백(레터박스)은 그림이 아니다. 원본 크기를 모르면 그림이 없는 것으로 본다.
 */
internal fun isOnFittedImage(
    tap: Offset,
    viewport: Size,
    imageSize: Size,
    scale: Float,
    offset: Offset,
): Boolean {
    if (!imageSize.isSpecified || imageSize.isEmpty()) return false
    val fitted = imageSize * ContentScale.Fit.computeScaleFactor(imageSize, viewport)
    val center = viewport.center
    // 그린 변환을 되돌려 확대 전 좌표로 옮긴 뒤 맞춘 그림 영역과 비교한다.
    val untransformed = center + (tap - center - offset) / scale
    return Rect(center - Offset(fitted.width / 2f, fitted.height / 2f), fitted).contains(untransformed)
}

private val IntSize.center: Offset get() = Offset(width / 2f, height / 2f)

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

@Preview(name = "썸네일 뷰어")
@Composable
private fun FullscreenImageViewerPreview() {
    ManyakTheme(darkTheme = false) {
        FullscreenImageViewer(imageUrl = "", onClose = {})
    }
}
