package app.manyak.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 조회 중 자리를 잡아 두는 골격. 카드와 **같은 행 구조**(3:4 표지 + 제목 줄 + 소개 두 줄 + 뱃지 줄 + 메타 줄)라
 * 목록이 도착할 때 요소가 튀지 않는다. 표지도 카드와 같은 [CoverWidth] 를 쓴다.
 *
 * 표시 여부는 호출부가 지연 판정으로 정한다 — 금방 끝나는 조회에서는 아예 그리지 않는다.
 */
@Composable
internal fun MyStoriesSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.studio_loading)
    val alpha = rememberSkeletonPulseAlpha()

    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
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
    Row(
        // 카드와 같은 자리 여백 — 목록이 도착할 때 표지 자리가 튀지 않는다.
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.Top,
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.width(CoverWidth).height(CoverHeight),
            alpha = alpha,
            shape = ManyakTheme.shapes.thumbnail,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = CoverHeight)
                    .padding(vertical = ManyakTheme.spacing.hairline),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            TextLinesPlaceholder(alpha = alpha)
            MetaLinePlaceholder(
                alpha = alpha,
                modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
            )
        }
    }
}

/** 제목 줄·소개 두 줄·뱃지 줄. 카드와 같은 순서로 쌓인다. */
@Composable
private fun TextLinesPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    // 글줄 높이를 dp 로 박아 두면 시스템 글자 크기를 키웠을 때 골격만 제자리에 남는다.
    val density = LocalDensity.current
    val typography = ManyakTheme.typography
    val titleHeight = with(density) { typography.bodyLargeStrong.fontSize.toDp() }
    val introHeight = with(density) { typography.bodyMedium.fontSize.toDp() }
    // 뱃지 높이는 상세와 같은 크기(`StoryBadgeScale.Large`)의 글줄과 위아래 여백에서 나온다.
    val badgeHeight =
        with(density) { typography.labelLarge.lineHeight.toDp() } + ManyakTheme.spacing.inline * 2

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(titleHeight),
            alpha = alpha,
        )
        SkeletonPlaceholder(modifier = Modifier.fillMaxWidth().height(introHeight), alpha = alpha)
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(INTRO_LAST_LINE_WIDTH_FRACTION).height(introHeight),
            alpha = alpha,
        )
        Row(
            modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            SkeletonPlaceholder(modifier = Modifier.width(BadgeWideWidth).height(badgeHeight), alpha = alpha)
            SkeletonPlaceholder(modifier = Modifier.width(BadgeNarrowWidth).height(badgeHeight), alpha = alpha)
        }
    }
}

/** 턴 수·제작일 자리. 카드와 같이 오른쪽 끝에 붙는다. */
@Composable
private fun MetaLinePlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val metaStyle = ManyakTheme.typography.bodyMedium
    val metaHeight = with(LocalDensity.current) { metaStyle.fontSize.toDp() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.End),
    ) {
        SkeletonPlaceholder(modifier = Modifier.width(MetaNarrowWidth).height(metaHeight), alpha = alpha)
        SkeletonPlaceholder(modifier = Modifier.width(MetaWideWidth).height(metaHeight), alpha = alpha)
    }
}

/** 첫 화면을 채우고도 남는 만큼. 카드가 그리드가 아니라 행이 되며 한 화면에 들어가는 수가 줄었다. */
private const val PLACEHOLDER_CARD_COUNT = 5

private const val TITLE_WIDTH_FRACTION = 0.75f

/** 소개 둘째 줄은 끝까지 차지하는 일이 드물어 짧게 그린다. */
private const val INTRO_LAST_LINE_WIDTH_FRACTION = 0.6f

private val BadgeWideWidth = 72.dp
private val BadgeNarrowWidth = 56.dp
private val MetaNarrowWidth = 32.dp
private val MetaWideWidth = 72.dp
