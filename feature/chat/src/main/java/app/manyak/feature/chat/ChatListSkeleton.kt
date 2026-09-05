package app.manyak.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 조회 중 자리를 잡아 두는 골격. 카드와 **같은 행 구조**(3:4 표지 + 제목 줄 + 미리보기 줄 + 메타 줄)라
 * 목록이 도착할 때 요소가 튀지 않는다.
 *
 * 표시 여부는 호출부가 지연 판정으로 정한다 — 금방 끝나는 조회에서는 아예 그리지 않는다.
 */
@Composable
internal fun ChatListSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.chat_list_loading)
    val alpha = rememberSkeletonPulseAlpha()

    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        contentPadding = contentPadding,
        // 아직 아무것도 없는 자리이므로 스크롤로 더 볼 것이 없다.
        userScrollEnabled = false,
    ) {
        items(PLACEHOLDER_ROW_COUNT) {
            RowPlaceholder(alpha = alpha)
        }
    }
}

@Composable
private fun RowPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    // 글줄 높이를 dp 로 박아 두면 시스템 글자 크기를 키웠을 때 골격만 제자리에 남는다.
    val density = LocalDensity.current
    val typography = ManyakTheme.typography
    val titleHeight = with(density) { typography.bodyLargeStrong.fontSize.toDp() }
    val previewHeight = with(density) { typography.bodyMedium.fontSize.toDp() }
    val metaHeight = with(density) { typography.bodyMedium.fontSize.toDp() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = ManyakTheme.spacing.compact,
                ),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.width(CoverWidth).aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
            alpha = alpha,
            shape = ManyakTheme.shapes.thumbnail,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(titleHeight),
                alpha = alpha,
            )
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth().height(previewHeight),
                alpha = alpha,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.End),
            ) {
                SkeletonPlaceholder(modifier = Modifier.width(MetaNarrowWidth).height(metaHeight), alpha = alpha)
                SkeletonPlaceholder(modifier = Modifier.width(MetaWideWidth).height(metaHeight), alpha = alpha)
            }
        }
    }
}

/** 첫 화면을 채우고도 남는 만큼. 카드가 그리드가 아니라 행이라 제작 탭보다 여러 줄이 들어간다. */
private const val PLACEHOLDER_ROW_COUNT = 8

private const val TITLE_WIDTH_FRACTION = 0.5f

private val MetaNarrowWidth = 32.dp
private val MetaWideWidth = 56.dp
