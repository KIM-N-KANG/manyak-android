package app.manyak.feature.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.core.ui.component.LoadFailedContent
import app.manyak.core.ui.component.ManyakPullToRefreshBox
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.component.withScreenMargins
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 홈 탭(오리지널 스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
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
                        Toast.makeText(context, R.string.story_refresh_failed, Toast.LENGTH_SHORT).show()
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

@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading)

    when {
        state.isLoading ->
            if (showSkeleton) {
                OriginalStoriesSkeleton(contentPadding = contentPadding.withScreenMargins(), modifier = modifier)
            } else {
                // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
                Box(modifier = modifier.fillMaxSize().padding(contentPadding))
            }

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(R.string.story_load_failed),
                onRetry = { onIntent(HomeIntent.Retry) },
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = ManyakTheme.spacing.gutter),
            )

        // 빈 목록은 섹션 자체를 그리지 않는다 — 제목만 남으면 없는 것을 있다고 말하는 셈이다.
        // 공식 계정에 스토리가 없는 환경에서만 나오는 자리라 안내 문구도 두지 않는다.
        state.stories.isEmpty() -> Box(modifier = modifier.fillMaxSize().padding(contentPadding))

        else ->
            OriginalStories(
                modifier = modifier,
                stories = state.stories,
                isRefreshing = state.isRefreshing,
                contentPadding = contentPadding,
                onOpenStory = onOpenStory,
                onRefresh = { onIntent(HomeIntent.Refresh) },
            )
    }
}

@Composable
private fun OriginalStories(
    stories: List<StorySummary>,
    isRefreshing: Boolean,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakPullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        contentPadding = contentPadding,
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(GRID_COLUMNS),
            contentPadding = contentPadding.withScreenMargins(),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            // 제목도 그리드의 한 줄이라 이 값이 제목 아래 간격까지 겸한다. 둘을 다르게 두려면 제목
            // 아이템에 padding 을 더한다.
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        ) {
            item(key = SECTION_TITLE_KEY, span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle()
            }
            items(stories, key = { story -> story.id }) { story ->
                StoryCard(story = story, onClick = { onOpenStory(story.id) })
            }
        }
    }
}

/** 셸 헤더의 탭 이름과 달리 목록 안에서 스크롤과 함께 밀려 올라가는 제목이다. */
@Composable
private fun SectionTitle(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = stringResource(R.string.home_original_stories),
        style = ManyakTheme.typography.titleMediumStrong,
        color = ManyakTheme.colors.text,
    )
}

internal const val GRID_COLUMNS = 2

private const val SECTION_TITLE_KEY = "section-title"

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
        previewStory(id = "1", title = "두 번째 시계공", turnCount = 1_284),
        previewStory(id = "2", title = "달빛 아래의 계약", turnCount = 312),
        previewStory(id = "3", title = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다", turnCount = 7),
        previewStory(id = "4", title = "잊힌 등대", turnCount = 0),
    )

private fun previewStory(
    id: String,
    title: String,
    turnCount: Long,
): StorySummary =
    StorySummary(
        id = id,
        title = title,
        authorNickname = "마냑",
        thumbnailUrl = null,
        oneLineIntro = "",
        genres = emptyList(),
        turnCount = turnCount,
    )
