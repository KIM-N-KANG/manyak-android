package app.manyak.feature.story

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage
import app.manyak.designsystem.R as DesignsystemR

/**
 * 썸네일 전체 화면 뷰어. **목적지가 아니라 상세 화면의 오버레이**다 — 되돌아갈 수 있는 자리가
 * 아니라 같은 화면의 일시 상태이고, 목적지로 두면 라우트 규칙상 `storyId` 만 받아 상세를 다시
 * 조회해야 해서 보고 있는 이미지 위에 골격을 다시 깔게 된다.
 *
 * 닫기 수단은 셋이다 — 닫기(X)·화면 탭·시스템 뒤로가기. 뒤로가기는 이 오버레이가 떠 있는 동안만
 * 흡수되므로 화면을 옮기지 않고 뷰어만 닫는다.
 */
@Composable
internal fun StoryImageViewer(
    imageUrl: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val dismissInteraction = remember { MutableInteractionSource() }
    val closeLabel = stringResource(R.string.story_detail_thumbnail_close)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ViewerScrim)
                // 화면 어디를 눌러도 닫힌다. 이미지 위에 얹는 눌림 표시는 두지 않는다.
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = onClose,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
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

/** 뷰어 바탕. 이미지를 `Fit` 으로 그려 남는 자리가 생기므로 불투명에 가깝게 덮는다. */
private val ViewerScrim = Color.Black.copy(alpha = 0.92f)

@Preview(name = "썸네일 뷰어")
@Composable
private fun StoryImageViewerPreview() {
    ManyakTheme(darkTheme = false) {
        StoryImageViewer(imageUrl = "", onClose = {})
    }
}
