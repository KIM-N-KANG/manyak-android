package app.manyak.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
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
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
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
            LoadFailed(
                modifier = modifier.fillMaxSize().padding(contentPadding),
                onRetry = { onIntent(HomeIntent.Retry) },
            )

        // 빈 목록은 섹션 자체를 그리지 않는다 — 제목만 남으면 없는 것을 있다고 말하는 셈이다.
        else ->
            OriginalStories(
                modifier = modifier,
                stories = state.stories,
                contentPadding = contentPadding,
            )
    }
}

@Composable
private fun OriginalStories(
    stories: List<StorySummary>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
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
            StoryCard(story = story)
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

@Composable
private fun LoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.home_load_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(text = stringResource(R.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
}

/**
 * 셸이 넘긴 여백에 화면 좌우 여백과 하단 여유를 더한다. 목록 항목이 화면 가장자리에 닿지 않게
 * 하면서도, 스크롤되는 콘텐츠가 헤더·탭 아래로 흘러 들어가는 성질은 그대로 둔다.
 */
@Composable
private fun PaddingValues.withScreenMargins(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + ManyakTheme.spacing.gutter,
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + ManyakTheme.spacing.gutter,
        bottom = calculateBottomPadding() + ManyakTheme.spacing.screenBottom,
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
            onIntent = {},
        )
    }
}

private fun previewStories(): List<StorySummary> =
    listOf(
        StorySummary(id = "1", title = "두 번째 시계공", authorNickname = "마냑", thumbnailUrl = null, turnCount = 1_284),
        StorySummary(id = "2", title = "달빛 아래의 계약", authorNickname = "마냑", thumbnailUrl = null, turnCount = 312),
        StorySummary(
            id = "3",
            title = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            authorNickname = "마냑",
            thumbnailUrl = null,
            turnCount = 7,
        ),
        StorySummary(id = "4", title = "잊힌 등대", authorNickname = "마냑", thumbnailUrl = null, turnCount = 0),
    )
