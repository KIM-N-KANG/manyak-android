package app.manyak.story.detail.presentation.component

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.story.R as StoryR

/**
 * 썸네일 전체 화면 뷰어. **목적지가 아니라 상세 화면의 오버레이**다 — 되돌아갈 수 있는 자리가
 * 아니라 같은 화면의 일시 상태이고, 목적지로 두면 라우트 규칙상 `storyId` 만 받아 상세를 다시
 * 조회해야 해서 보고 있는 이미지 위에 골격을 다시 깔게 된다.
 *
 * 닫기 수단은 셋이다 — 닫기(X)·화면 탭·시스템 뒤로가기. 뒤로가기는 이 오버레이가 떠 있는 동안만
 * 흡수되므로 화면을 옮기지 않고 뷰어만 닫는다.
 *
 * 핀치로 확대·이동하고 더블탭은 확대와 원래 크기를 오간다. 확대 상태는 뷰어를 닫거나 화면이
 * 재생성되면 사라진다 — 다시 열었을 때 어디를 보고 있었는지 이어 줄 이유가 없다.
 */
@Composable
internal fun StoryImageViewer(
    imageUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val closeLabel = stringResource(StoryR.string.story_detail_thumbnail_close)
    val zoom = remember { ZoomState() }
    val transformState =
        rememberTransformableState { zoomChange, panChange, _ ->
            zoom.transform(zoomChange, panChange)
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ViewerScrim)
                .semantics {
                    onClick(label = closeLabel) {
                        onClose()
                        true
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(
                        // 화면 어디를 눌러도 닫힌다. 이미지 위에 얹는 눌림 표시는 두지 않는다.
                        onTap = { onClose() },
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
            tint = Color.White,
        )
    }
}

/** 확대 배율과 이동량. 이동은 뷰포트 밖으로 이미지가 빠져나가지 않는 범위로만 허용한다. */
private class ZoomState {
    var scale by mutableFloatStateOf(MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport = IntSize.Zero

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

private val IntSize.center: Offset get() = Offset(width / 2f, height / 2f)

/** 뷰어 바탕. 이미지를 `Fit` 으로 그려 남는 자리가 생기므로 불투명에 가깝게 덮는다. */
private val ViewerScrim = Color.Black.copy(alpha = 0.92f)

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

@Preview(name = "썸네일 뷰어")
@Composable
private fun StoryImageViewerPreview() {
    ManyakTheme(darkTheme = false) {
        StoryImageViewer(imageUrl = "", onClose = {})
    }
}
