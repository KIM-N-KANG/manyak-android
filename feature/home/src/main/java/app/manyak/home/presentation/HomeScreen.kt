package app.manyak.home.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.entity.HomeSection
import app.manyak.home.presentation.component.HomeSectionRow
import app.manyak.home.presentation.component.HomeSectionsSkeleton
import app.manyak.home.presentation.component.StoryCard
import app.manyak.common.R as CommonR
import app.manyak.home.R as HomeR

/**
 * 홈 탭(홈 섹션). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로 `Modifier.padding` 이 아니라 목록의
 * `contentPadding` 으로 넘긴다. 그래야 콘텐츠가 헤더 아래로 흘러 들어간다.
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

@Composable
private fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)
    val listPadding = contentPadding.withSectionListBottom()

    when {
        state.isLoading && state.sections.isEmpty() ->
            if (showProgress) HomeSectionsSkeleton(contentPadding = listPadding, modifier = modifier)

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(CommonR.string.story_load_failed),
                onRetry = { onIntent(HomeIntent.Retry) },
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = ManyakTheme.spacing.gutter),
            )

        state.sections.isEmpty() ->
            Box(modifier = modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(HomeR.string.home_stories_empty),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }

        else ->
            ManyakPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(HomeIntent.Refresh) },
                contentPadding = contentPadding,
                modifier = modifier,
            ) {
                HomeSections(sections = state.sections, listPadding = listPadding, onOpenStory = onOpenStory)
            }
    }
}

@Composable
private fun HomeSections(
    sections: List<HomeSection>,
    listPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        itemsIndexed(sections, key = { index, section -> section.listKey(index) }) { index, section ->
            val sectionKey = section.listKey(index)
            HomeSectionRow(name = section.name, cardCount = section.stories.size) { cardWidth ->
                itemsIndexed(section.stories, key = { _, story -> story.id }) { position, story ->
                    StoryCard(
                        story = story,
                        onClick = {
                            analytics.track(
                                AnalyticsEvent.StoryCardClicked(story.id, position, StoryListSection.ORIGINAL),
                            )
                            onOpenStory(story.id)
                        },
                        // 같은 스토리가 여러 섹션에 있으면 섹션마다 따로 보이므로 노출도 섹션마다 센다.
                        modifier =
                            Modifier
                                .width(cardWidth)
                                .trackImpression(impressions, key = "$sectionKey/${story.id}") {
                                    val event =
                                        AnalyticsEvent.StoryCardImpressed(story.id, position, StoryListSection.ORIGINAL)
                                    analytics.track(event)
                                },
                    )
                }
            }
        }
    }
}

/** 기본 섹션은 id 가 없어 이름과 순번으로 가른다. */
private fun HomeSection.listKey(index: Int): String = id ?: "default/$index/$name"

/** 셸 여백 위에 바로 첫 섹션을 붙이고 마지막 섹션 아래만 띄운다. */
@Composable
private fun PaddingValues.withSectionListBottom(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + ManyakTheme.spacing.gutter,
    )
}

@Preview(showBackground = true, name = "홈 섹션")
@Composable
private fun HomeContentPreview() {
    ManyakTheme(darkTheme = false) {
        HomeContent(
            state =
                HomeUiState(
                    isLoading = false,
                    sections =
                        listOf(
                            HomeSection(id = null, name = "인기 스토리", stories = previewStories()),
                            HomeSection(id = null, name = "오리지널 스토리", stories = previewStories().reversed()),
                        ),
                ),
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
        previewStory(id = "1", title = "두 번째 시계공", genres = listOf("판타지", "미스터리"), isOriginal = true),
        previewStory(id = "2", title = "달빛 아래의 계약", genres = listOf("로맨스", "판타지", "스릴러", "코미디")),
        previewStory(id = "3", title = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다", genres = emptyList()),
    )

private fun previewStory(
    id: String,
    title: String,
    genres: List<String>,
    isOriginal: Boolean = false,
): StorySummary =
    StorySummary(
        id = id,
        title = title,
        authorNickname = null,
        thumbnailUrl = null,
        oneLineIntro = "멈춘 시계탑을 고치는 견습공이 시간의 틈에서 다른 자신을 만나는 이야기",
        genres = genres,
        likeCount = 0,
        turnCount = 1_284,
        createdDate = null,
        isOriginal = isOriginal,
    )
