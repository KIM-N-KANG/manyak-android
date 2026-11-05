package app.manyak.home.explore.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryListSection
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.analytics.presentation.rememberImpressionTracker
import app.manyak.analytics.presentation.trackImpression
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.LoadMoreFooter
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 탐색과 검색 결과가 함께 쓰는 타일 그리드. 끝 두 줄 앞에 닿으면 다음 페이지를 잇고, 다음 페이지 조회 중이거나
 * 실패하면 목록 끝에 스피너나 다시 시도를 둔다. 카드 이벤트는 [section] 으로 목록을 구분한다.
 *
 * 칸은 순번으로 자리를 지킨다. 새로 섞어도 칸이 그대로 남아 칸 안에서 타일만 바뀐다.
 */
@Composable
@Suppress("LongParameterList")
internal fun StoryTileGrid(
    stories: List<StorySummary>,
    section: StoryListSection,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    isLoadingMore: Boolean,
    loadMoreFailed: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenStory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()
    LoadMoreWhenGridEnds(gridState = gridState, canLoadMore = canLoadMore, onLoadMore = onLoadMore)

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        state = gridState,
        columns = GridCells.Fixed(EXPLORE_COLUMNS),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
    ) {
        itemsIndexed(stories) { position, story ->
            ExploreTile(
                story = story,
                position = position,
                onClick = {
                    analytics.track(AnalyticsEvent.StoryCardClicked(story.id, position, section))
                    onOpenStory(story.id)
                },
                modifier =
                    Modifier.trackImpression(impressions, key = story.id) {
                        analytics.track(AnalyticsEvent.StoryCardImpressed(story.id, position, section))
                    },
            )
        }
        if (isLoadingMore || loadMoreFailed) {
            item(key = LOAD_MORE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreFooter(isLoading = isLoadingMore, onRetry = onLoadMore)
            }
        }
    }
}

/**
 * 목록 끝 두 줄 앞에 닿으면 다음 커서를 잇는다. **실패한 뒤에는 자동으로 다시 요청하지 않는다.**
 * 끝에 머무는 동안 같은 실패를 반복하게 되므로 그 자리에서는 목록 끝의 다시 시도가 이어받는다.
 */
@Composable
private fun LoadMoreWhenGridEnds(
    gridState: LazyGridState,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    // 스크롤 위치를 컴포지션에서 읽으면 프레임마다 다시 그린다. 배치 정보는 효과 안에서만 본다.
    LaunchedEffect(gridState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastIndex >= layout.totalItemsCount - 1 - LOAD_MORE_AHEAD_ITEMS
        }.distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
}

/** 끝에 닿기 전에 미리 읽기 시작하는 거리. 두 줄이다. */
private const val LOAD_MORE_AHEAD_ITEMS = EXPLORE_COLUMNS * 2

private const val LOAD_MORE_KEY = "load-more"
