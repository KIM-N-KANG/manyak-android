package app.manyak.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 조회 중 자리를 잡아 두는 골격. 카드와 **같은 구조**(3:4 표지 + 제목 줄 + 제작자 줄)라
 * 목록이 도착할 때 요소가 튀지 않는다.
 *
 * 섹션 제목 자리는 두지 않는다 — 카드와 달리 무엇이 올지 이미 아는 고정 문구라 흉내 낼 것이 없고,
 * 실제 제목은 목록이 도착해 그릴 것이 생겼을 때 함께 나타난다.
 *
 * 표시 여부는 호출부가 지연 판정으로 정한다 — 금방 끝나는 조회에서는 아예 그리지 않는다.
 */
@Composable
internal fun OriginalStoriesSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.home_loading)
    val alpha = rememberSkeletonPulseAlpha()

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        // 아직 아무것도 없는 자리이므로 스크롤로 더 볼 것이 없다.
        userScrollEnabled = false,
    ) {
        items(PLACEHOLDER_CARD_COUNT) {
            CardPlaceholder(alpha = alpha)
        }
    }
}

@Composable
private fun CardPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
            alpha = alpha,
            shape = ManyakTheme.shapes.thumbnail,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline)) {
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(TitleLineHeight),
                alpha = alpha,
            )
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth(AUTHOR_WIDTH_FRACTION).height(AuthorLineHeight),
                alpha = alpha,
            )
        }
    }
}

/** 스펙이 정한 골격 카드 수. 첫 화면을 채우고도 남는 만큼이다. */
private const val PLACEHOLDER_CARD_COUNT = 6

private const val TITLE_WIDTH_FRACTION = 0.75f
private const val AUTHOR_WIDTH_FRACTION = 0.5f

private val TitleLineHeight = 24.dp
private val AuthorLineHeight = 20.dp
