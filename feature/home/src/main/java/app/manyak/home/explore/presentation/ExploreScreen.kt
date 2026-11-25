package app.manyak.home.explore.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.analytics.entity.StoryListSection
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.explore.presentation.component.ExploreGridSkeleton
import app.manyak.home.explore.presentation.component.StoryTileGrid
import app.manyak.common.R as CommonR
import app.manyak.home.R as HomeR

/**
 * 탐색 탭. 공개 스토리 타일을 화면 좌우 끝까지 두 열로 붙인 그리드다. 헤더와 하단 탭은 셸이 그린다.
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
private fun ExploreGrid(
    state: ExploreUiState,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (ExploreIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(ExploreIntent.Refresh) },
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        StoryTileGrid(
            stories = state.stories,
            section = StoryListSection.EXPLORE,
            gridState = gridState,
            contentPadding = contentPadding,
            isLoadingMore = state.isLoadingMore,
            loadMoreFailed = state.loadMoreFailed,
            canLoadMore = state.hasMore && !state.isLoadingMore && !state.loadMoreFailed && !state.isRefreshing,
            onLoadMore = { onIntent(ExploreIntent.LoadMore) },
            onOpenStory = onOpenStory,
        )
    }
}
