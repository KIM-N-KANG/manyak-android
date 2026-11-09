package app.manyak.story.detail.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 조회 중 자리를 잡아 두는 골격. 본문과 **같은 구조에 같은 간격**이라 본문이 도착할 때 요소가 튀지
 * 않는다 — 표지 → 제목 → 한 줄 소개 → 장르 뱃지 → 첫 섹션(라벨 + 여러 줄 글)까지 그린다. 그 아래
 * 섹션들은 어느 기기에서도 표지 높이에 밀려 접힘 밖이라 그리지 않는다.
 *
 * 글줄 자리의 높이는 실제로 들어올 타이포 롤에서 가져온다. 숫자로 박아 두면 서체나 큰 글자 설정이
 * 바뀔 때 골격만 제자리에 남는다.
 *
 * 표시 여부는 호출부가 지연 판정으로 정한다 — 금방 끝나는 조회에서는 아예 그리지 않는다.
 */
@Composable
internal fun StoryDetailSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonPulseAlpha()

    Column(
        modifier = modifier.fillMaxSize(),
        // 본문 목록의 항목 사이 간격.
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        SkeletonOverview(alpha = alpha)
        SkeletonSection(alpha = alpha)
    }
}

/** 표지와 제목 묶음. 본문의 개요 항목과 같은 중첩·간격이다. */
@Composable
private fun SkeletonOverview(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Column {
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
                alpha = alpha,
                shape = RectangleShape,
            )
            HorizontalDivider(thickness = StoryHeroBorderWidth, color = ManyakTheme.colors.border)
        }
        Column(
            modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                SkeletonTextLine(
                    style = ManyakTheme.typography.headlineSmall,
                    alpha = alpha,
                    widthFraction = TITLE_WIDTH_FRACTION,
                )
                SkeletonTextLine(
                    style = ManyakTheme.typography.bodyLarge,
                    alpha = alpha,
                    widthFraction = INTRO_WIDTH_FRACTION,
                )
            }
            SkeletonBadgeRow(alpha = alpha)
        }
    }
}

/** 라벨과 여러 줄 글로 된 섹션 하나. 주요 내용·시작 상황 어느 쪽이 먼저 와도 같은 모양이다. */
@Composable
private fun SkeletonSection(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        SkeletonTextLine(
            style = ManyakTheme.typography.titleMediumStrong,
            alpha = alpha,
            widthFraction = SECTION_LABEL_WIDTH_FRACTION,
        )
        // 줄 사이를 벌리지 않는다 — 한 문단이라 간격은 각 줄이 든 행간이 전부다.
        Column(verticalArrangement = Arrangement.Top) {
            repeat(SECTION_BODY_LINES) { index ->
                SkeletonTextLine(
                    style = ManyakTheme.typography.bodyLarge,
                    alpha = alpha,
                    widthFraction = if (index == SECTION_BODY_LINES - 1) LAST_LINE_WIDTH_FRACTION else 1f,
                )
            }
        }
    }
}

/** 장르 뱃지 줄. 뱃지 높이는 상세 뱃지(`StoryBadgeScale.Large`)의 글줄과 위아래 여백에서 나온다. */
@Composable
private fun SkeletonBadgeRow(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val badgeStyle = ManyakTheme.typography.labelLarge
    val badgeHeight =
        with(LocalDensity.current) { badgeStyle.lineHeight.toDp() } + ManyakTheme.spacing.inline * 2

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        BadgeWidths.forEach { width ->
            SkeletonPlaceholder(
                modifier = Modifier.width(width).height(badgeHeight),
                alpha = alpha,
            )
        }
    }
}

/**
 * 글줄 한 자리. 차지하는 높이는 실제 글줄의 행간과 같게 두고 회색 막대는 글자 크기만큼만 그린다 —
 * 행간까지 칠하면 본문보다 두꺼운 덩어리가 되어 같은 자리로 보이지 않는다.
 */
@Composable
private fun SkeletonTextLine(
    style: TextStyle,
    alpha: Float,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    val density = LocalDensity.current
    val lineHeight = with(density) { style.lineHeight.toDp() }
    val glyphHeight = with(density) { style.fontSize.toDp() }

    Box(
        modifier = modifier.fillMaxWidth().height(lineHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(widthFraction).height(glyphHeight),
            alpha = alpha,
        )
    }
}

private const val TITLE_WIDTH_FRACTION = 0.6f
private const val INTRO_WIDTH_FRACTION = 0.9f
private const val SECTION_LABEL_WIDTH_FRACTION = 0.3f
private const val LAST_LINE_WIDTH_FRACTION = 0.55f
private const val SECTION_BODY_LINES = 3

/** 장르 뱃지는 길이가 제각각이라 같은 폭으로 늘어놓으면 글자가 들어올 자리로 보이지 않는다. */
private val BadgeWidths: List<Dp> = listOf(72.dp, 56.dp, 88.dp)

@Preview(showBackground = true, name = "스토리 상세 · 골격")
@Composable
private fun StoryDetailSkeletonPreview() {
    ManyakTheme(darkTheme = false) {
        StoryDetailSkeleton()
    }
}
