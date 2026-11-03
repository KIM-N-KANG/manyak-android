package app.manyak.home.explore.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryListSection
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.analytics.presentation.rememberImpressionTracker
import app.manyak.analytics.presentation.trackImpression
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.LoadMoreFooter
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.explore.presentation.component.EXPLORE_COLUMNS
import app.manyak.home.explore.presentation.component.ExploreGridSkeleton
import app.manyak.home.explore.presentation.component.ExploreTile
import kotlinx.coroutines.flow.distinctUntilChanged
import app.manyak.common.R as CommonR
import app.manyak.home.R as HomeR

/**
 * 탐색 탭. 공개 스토리 표지를 화면 좌우 끝까지 세 열로 붙인 그리드다. 헤더와 하단 탭은 셸이 그린다.
 *
 * [entryRequest] 는 다른 탭에서 탐색으로 들어올 때마다 셸이 올리는 값이다. 값이 바뀔 때만 새로 섞어 맨 위부터
 * 보이고, 상세에서 돌아오면 같은 값이라 보던 순서와 위치가 남는다.
 */
@Composable
fun ExploreScreen(
    contentPadding: PaddingValues,
    entryRequest: Int,
    onOpenStory: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, entryRequest) { viewModel.onIntent(ExploreIntent.Enter(entryRequest)) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    ExploreEffect.ShowRefreshFailed ->
                        Toast.makeText(context, CommonR.string.story_refresh_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ExploreContent(
        state = state,
        contentPadding = contentPadding,
        onOpenStory = onOpenStory,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun ExploreContent(
    state: ExploreUiState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (ExploreIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)
    // 탭 진입으로 새 순서를 받으면 맨 위에서 시작하고, 그 사이 구성 변경과 상세 복귀에서는 위치를 지킨다.
    val gridState = rememberSaveable(state.entryVersion, saver = LazyGridState.Saver) { LazyGridState() }

    when {
        state.isLoading && state.stories.isEmpty() ->
            if (showProgress) ExploreGridSkeleton(contentPadding = contentPadding, modifier = modifier)

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(CommonR.string.story_load_failed),
                onRetry = { onIntent(ExploreIntent.Retry) },
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = ManyakTheme.spacing.gutter),
            )

        state.stories.isEmpty() ->
            Box(modifier = modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(HomeR.string.home_stories_empty),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }

        else ->
            ExploreGrid(
                state = state,
                gridState = gridState,
                contentPadding = contentPadding,
                onOpenStory = onOpenStory,
                onIntent = onIntent,
                modifier = modifier,
            )
    }
}

@Composable
@Suppress("LongParameterList")
private fun ExploreGrid(
    state: ExploreUiState,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (ExploreIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()
    LoadMoreWhenGridEnds(gridState = gridState, state = state, onLoadMore = { onIntent(ExploreIntent.LoadMore) })

    ManyakPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(ExploreIntent.Refresh) },
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Fixed(EXPLORE_COLUMNS),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        ) {
            // 칸은 순번으로 자리를 지킨다. 새로 섞어도 칸이 그대로 남아 칸 안에서 표지만 바뀐다.
            itemsIndexed(state.stories) { position, story ->
                ExploreTile(
                    story = story,
                    position = position,
                    onClick = {
                        analytics.track(AnalyticsEvent.StoryCardClicked(story.id, position, StoryListSection.EXPLORE))
                        onOpenStory(story.id)
                    },
                    modifier =
                        Modifier.trackImpression(impressions, key = story.id) {
                            analytics.track(
                                AnalyticsEvent.StoryCardImpressed(story.id, position, StoryListSection.EXPLORE),
                            )
                        },
                )
            }
            if (state.isLoadingMore || state.loadMoreFailed) {
                item(key = LOAD_MORE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreFooter(
                        isLoading = state.isLoadingMore,
                        onRetry = { onIntent(ExploreIntent.LoadMore) },
                    )
                }
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
    state: ExploreUiState,
    onLoadMore: () -> Unit,
) {
    val canLoadMore = state.hasMore && !state.isLoadingMore && !state.loadMoreFailed && !state.isRefreshing

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
