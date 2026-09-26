package app.manyak.home.presentation

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.LoadMoreFooter
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.withScreenMargins
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.presentation.component.StoryCard
import app.manyak.home.presentation.component.StoryGridSkeleton
import app.manyak.home.presentation.component.StoryListToolbar
import app.manyak.home.presentation.component.rememberToolbarHideState
import kotlinx.coroutines.flow.distinctUntilChanged
import app.manyak.common.R as CommonR
import app.manyak.home.R as HomeR

/**
 * 홈 탭(공개 스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로 `Modifier.padding` 이 아니라 목록의
 * `contentPadding` 으로 넘긴다 — 그래야 콘텐츠가 헤더 아래로 흘러 들어간다.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    HomeEffect.ShowRefreshFailed ->
                        Toast.makeText(context, CommonR.string.story_refresh_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onOpenStory = onOpenStory,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * 필터·정렬 바는 모든 상태에서 목록 위에 겹쳐 둔다. 목록 쪽은 바 높이만큼 위 여백을 더 비운다.
 */
@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var toolbarHeightPx by remember { mutableIntStateOf(0) }
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top =
                contentPadding.calculateTopPadding() +
                    with(density) { toolbarHeightPx.toDp() } +
                    ManyakTheme.spacing.compact,
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding(),
        )

    // 첫 페이지가 새로 오면(조건 변경·새로고침) 새 목록의 맨 위에서 시작하고, 그 사이 구성 변경에서는 위치를 지킨다.
    // 새 응답을 기다리는 동안 남겨 둔 이전 목록은 보던 위치 그대로 둔다.
    val gridState = rememberSaveable(state.firstPageVersion, saver = LazyGridState.Saver) { LazyGridState() }
    val hideState = rememberToolbarHideState()
    val alwaysShownOffsetPx = with(density) { ToolbarAlwaysShownScroll.toPx() }
    val toolbarVisible by remember(gridState, alwaysShownOffsetPx) {
        derivedStateOf {
            val nearTop =
                gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset <= alwaysShownOffsetPx
            nearTop || !hideState.hidden
        }
    }

    Box(modifier = modifier.fillMaxSize().nestedScroll(hideState)) {
        StoriesContent(
            state = state,
            gridState = gridState,
            contentPadding = contentPadding,
            listPadding = listPadding,
            onOpenStory = onOpenStory,
            onIntent = onIntent,
        )
        StoryListToolbar(
            modifier =
                Modifier
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ).onSizeChanged { size -> toolbarHeightPx = size.height },
            query = state.query,
            visible = toolbarVisible,
            onSelectFilter = { filter -> onIntent(HomeIntent.SelectFilter(filter)) },
            onSelectSort = { sort -> onIntent(HomeIntent.SelectSort(sort)) },
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun StoriesContent(
    state: HomeUiState,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (HomeIntent) -> Unit,
) {
    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)
    // 조건을 바꾼 직후에는 이전 목록이 남아 있다. 응답이 늦을 때만 흐려 새 목록을 기다리는 중임을 알린다.
    val listAlpha by animateFloatAsState(if (showProgress) STALE_LIST_ALPHA else 1f, label = "staleList")

    when {
        state.isLoading && state.stories.isEmpty() ->
            if (showProgress) StoryGridSkeleton(contentPadding = listPadding.withScreenMargins())

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(CommonR.string.story_load_failed),
                onRetry = { onIntent(HomeIntent.Retry) },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(listPadding)
                        .padding(horizontal = ManyakTheme.spacing.gutter),
            )

        state.stories.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize().padding(listPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(HomeR.string.home_stories_empty),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }

        else ->
            StoryGrid(
                modifier = Modifier.alpha(listAlpha),
                state = state,
                gridState = gridState,
                contentPadding = contentPadding,
                listPadding = listPadding,
                onOpenStory = onOpenStory,
                onIntent = onIntent,
            )
    }
}

@Composable
@Suppress("LongParameterList")
private fun StoryGrid(
    state: HomeUiState,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    listPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()
    LoadMoreWhenGridEnds(gridState = gridState, state = state, onLoadMore = { onIntent(HomeIntent.LoadMore) })

    // 당김 표시자는 셸 여백에 둬 바 뒤에서 내려오게 한다.
    ManyakPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(HomeIntent.Refresh) },
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = listPadding.withScreenMargins(),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        ) {
            itemsIndexed(state.stories, key = { _, story -> story.id }) { index, story ->
                StoryCard(
                    story = story,
                    onClick = {
                        analytics.track(AnalyticsEvent.StoryCardClicked(story.id, index, StoryListSection.ORIGINAL))
                        onOpenStory(story.id)
                    },
                    modifier =
                        Modifier.trackImpression(impressions, key = story.id) {
                            analytics.track(
                                AnalyticsEvent.StoryCardImpressed(story.id, index, StoryListSection.ORIGINAL),
                            )
                        },
                )
            }
            if (state.isLoadingMore || state.loadMoreFailed) {
                item(key = LOAD_MORE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreFooter(
                        isLoading = state.isLoadingMore,
                        onRetry = { onIntent(HomeIntent.LoadMore) },
                    )
                }
            }
        }
    }
}

/**
 * 목록 끝 두 줄 앞에 닿으면 다음 커서를 잇는다. **실패한 뒤에는 자동으로 다시 요청하지 않는다** —
 * 끝에 머무는 동안 같은 실패를 반복하게 되므로, 그 자리에서는 목록 끝의 다시 시도가 이어받는다.
 */
@Composable
private fun LoadMoreWhenGridEnds(
    gridState: LazyGridState,
    state: HomeUiState,
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

internal const val GRID_COLUMNS = 2

/** 끝에 닿기 전에 미리 읽기 시작하는 거리. 두 줄이다. */
private const val LOAD_MORE_AHEAD_ITEMS = GRID_COLUMNS * 2

private const val LOAD_MORE_KEY = "load-more"

/** 새 조건의 응답을 기다리는 동안 남겨 둔 이전 목록의 불투명도. 읽을 수는 있되 곧 바뀔 것임이 보이는 정도다. */
private const val STALE_LIST_ALPHA = 0.5f

/** 목록 맨 위에서 이만큼 안쪽이면 스크롤 방향과 무관하게 바를 보인다. 바 높이보다 크게 둔다. */
private val ToolbarAlwaysShownScroll: Dp = 64.dp

@Preview(showBackground = true, name = "홈 · 목록")
@Composable
private fun HomeContentPreview() {
    ManyakTheme(darkTheme = false) {
        HomeContent(
            state = HomeUiState(isLoading = false, stories = previewStories()),
            contentPadding = PaddingValues(),
            onOpenStory = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "홈 · 조회 실패")
@Composable
private fun HomeLoadFailedPreview() {
    ManyakTheme(darkTheme = false) {
        HomeContent(
            state = HomeUiState(isLoading = false, loadFailed = true),
            contentPadding = PaddingValues(),
            onOpenStory = {},
            onIntent = {},
        )
    }
}

private fun previewStories(): List<StorySummary> =
    listOf(
        previewStory(id = "1", title = "두 번째 시계공", turnCount = 1_284, isOriginal = true),
        previewStory(id = "2", title = "달빛 아래의 계약", turnCount = 312, isOriginal = false),
        previewStory(id = "3", title = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다", turnCount = 7, isOriginal = true),
        previewStory(id = "4", title = "잊힌 등대", turnCount = 0, isOriginal = false),
    )

private fun previewStory(
    id: String,
    title: String,
    turnCount: Long,
    isOriginal: Boolean,
): StorySummary =
    StorySummary(
        id = id,
        title = title,
        authorNickname = "마냑",
        thumbnailUrl = null,
        oneLineIntro = "",
        genres = emptyList(),
        likeCount = 0,
        turnCount = turnCount,
        createdDate = null,
        isOriginal = isOriginal,
    )
