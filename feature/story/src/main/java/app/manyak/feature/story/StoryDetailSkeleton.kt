package app.manyak.feature.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 조회 중 자리를 잡아 두는 골격. 본문과 **같은 구조**(3:4 히어로 + 제목 + 소개 + 뱃지 줄)라
 * 본문이 도착할 때 요소가 튀지 않는다.
 *
 * 표시 여부는 호출부가 지연 판정으로 정한다 — 금방 끝나는 조회에서는 아예 그리지 않는다.
 */
@Composable
internal fun StoryDetailSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonPulseAlpha()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
            alpha = alpha,
            shape = ManyakTheme.shapes.thumbnail,
        )
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(TitleHeight),
            alpha = alpha,
        )
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth().height(LineHeight),
            alpha = alpha,
        )
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(BADGE_WIDTH_FRACTION).height(LineHeight),
            alpha = alpha,
        )
    }
}

private const val TITLE_WIDTH_FRACTION = 0.6f
private const val BADGE_WIDTH_FRACTION = 0.4f

private val TitleHeight = 24.dp
private val LineHeight = 16.dp
