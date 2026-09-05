package app.manyak.feature.studio

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.CreateButtonSource
import app.manyak.analytics.entity.StoryListSection
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.analytics.presentation.rememberImpressionTracker
import app.manyak.analytics.presentation.trackImpression
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.withRowListMargins
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.common.R as CommonR
import app.manyak.report.R as ReportR

/**
 * 제작 탭(내가 만든 스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로 목록에는 `Modifier.padding` 이 아니라
 * 목록의 `contentPadding` 으로 넘긴다 — 그래야 콘텐츠가 헤더 아래로 흘러 들어간다.
 *
 * 제작 퍼널 진입 FAB 과 이어서 만들기 배너는 셸이 아니라 이 화면이 소유한다. 진행 레코드가 있으면
 * 상단에 배너를 표시하고, FAB 등 배너가 아닌 경로의 진입은 이어서/새로 만들기 다이얼로그로 묻는다.
 *
 * 목록 조회는 화면이 보일 때 시작한다. 퍼널·채팅방은 이 화면 위가 아니라 셸 위에 쌓여 돌아와도
 * ViewModel 이 그대로 살아 있으므로, 조회 시점을 화면 수명에 맞춰야 떠난 사이의 변화가 반영된다.
 */
@Composable
fun StudioScreen(
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onCreateStory: () -> Unit,
    onResumeCreation: (CreationResumePoint) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnCreateStory by rememberUpdatedState(onCreateStory)
    val currentOnResumeCreation by rememberUpdatedState(onResumeCreation)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    StudioEffect.NavigateToCreate -> currentOnCreateStory()
                    is StudioEffect.NavigateToResume -> currentOnResumeCreation(effect.resumePoint)

                    StudioEffect.ShowStoryDeleted ->
                        Toast.makeText(context, R.string.studio_story_deleted, Toast.LENGTH_SHORT).show()

                    StudioEffect.ShowStoryDeleteFailed ->
                        Toast.makeText(context, R.string.studio_story_delete_failed, Toast.LENGTH_SHORT).show()

                    StudioEffect.ShowRefreshFailed ->
                        Toast.makeText(context, CommonR.string.story_refresh_failed, Toast.LENGTH_SHORT).show()

                    StudioEffect.ShowReportSubmitted ->
                        Toast.makeText(context, ReportR.string.story_report_submitted, Toast.LENGTH_SHORT).show()

                    StudioEffect.ShowReportFailed ->
                        Toast.makeText(context, ReportR.string.story_report_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 화면을 떠난 사이 늘어난 목록을 반영한다 — 스토리를 완성하고 채팅으로 넘어갔다 돌아온 자리가 대표적이다.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(StudioIntent.ScreenShown) }

    StudioContent(
        state = state,
        contentPadding = contentPadding,
        onOpenStory = onOpenStory,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun StudioContent(
    state: StudioUiState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (StudioIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading)
    val analytics = LocalAnalytics.current

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading ->
                StoriesStatus(state = state, contentPadding = contentPadding, onIntent = onIntent) {
                    if (showSkeleton) {
                        MyStoriesSkeleton()
                    }
                    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
                }

            state.loadFailed ->
                StoriesStatus(state = state, contentPadding = contentPadding, onIntent = onIntent) {
                    LoadFailedContent(
                        message = stringResource(CommonR.string.story_load_failed),
                        onRetry = { onIntent(StudioIntent.Retry) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
                    )
                }

            state.stories.isEmpty() ->
                StoriesStatus(state = state, contentPadding = contentPadding, onIntent = onIntent) {
                    EmptyStories(modifier = Modifier.fillMaxSize())
                }

            else ->
                MyStories(
                    stories = state.stories,
                    banner = state.pendingBanner,
                    isRefreshing = state.isRefreshing,
                    contentPadding = contentPadding,
                    onOpenStory = onOpenStory,
                    onIntent = onIntent,
                )
        }

        CreateStoryFab(
            onClick = {
                // 앱은 빈 목록에도 FAB 하나만 두므로 출처는 늘 fab 이다.
                analytics.track(AnalyticsEvent.StoryListCreateButtonClicked(CreateButtonSource.FAB))
                onIntent(StudioIntent.CreateStory)
            },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(contentPadding)
                    .padding(ManyakTheme.spacing.gutter),
        )
    }

    StudioDialogs(state = state, onIntent = onIntent)
}

/**
 * 목록이 없는 상태(조회 중·실패·빈 목록)의 자리. 스크롤할 것이 없으므로 chrome 여백을 화면에
 * 씌우고, 배너를 상단에 고정한 뒤 나머지를 [content] 에 준다.
 *
 * 배너와 [content] 사이는 목록이 배너와 첫 카드 사이에 두는 것과 같은 간격이다 — 골격이 도착한
 * 목록으로 바뀔 때 첫 줄이 위아래로 튀지 않는다.
 */
@Composable
private fun StoriesStatus(
    state: StudioUiState,
    contentPadding: PaddingValues,
    onIntent: (StudioIntent) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        state.pendingBanner?.let { banner ->
            PendingCreationBannerRow(
                banner = banner,
                onResume = { onIntent(StudioIntent.ResumeCreation) },
                modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun MyStories(
    stories: List<StorySummary>,
    banner: PendingCreationBanner?,
    isRefreshing: Boolean,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (StudioIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()
    ManyakPullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = { onIntent(StudioIntent.Refresh) },
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 좌우 여백은 카드가 스스로 갖는다 — 채팅 목록과 같은 리듬이다.
            contentPadding = contentPadding.withRowListMargins(),
        ) {
            // 배너도 목록과 함께 스크롤된다 — 맨 위로 돌아오면 다시 보이므로 진입을 잃지 않는다.
            banner?.let {
                item(key = PENDING_BANNER_KEY) {
                    PendingCreationBannerRow(
                        banner = it,
                        onResume = { onIntent(StudioIntent.ResumeCreation) },
                        // 카드가 위쪽 여백을 스스로 갖고 있어, 배너 아래 같은 값을 더하면 둘 사이가 gutter 가 된다.
                        modifier =
                            Modifier
                                .padding(horizontal = ManyakTheme.spacing.gutter)
                                .padding(bottom = ManyakTheme.spacing.compact),
                    )
                }
            }
            itemsIndexed(stories, key = { _, story -> story.id }) { index, story ->
                MyStoryCard(
                    story = story,
                    onClick = {
                        analytics.track(AnalyticsEvent.StoryCardClicked(story.id, index, StoryListSection.CREATED))
                        onOpenStory(story.id)
                    },
                    onOptionsClick = { onIntent(StudioIntent.OpenStoryOptions(story)) },
                    modifier =
                        Modifier.trackImpression(impressions, key = story.id) {
                            analytics.track(
                                AnalyticsEvent.StoryCardImpressed(story.id, index, StoryListSection.CREATED),
                            )
                        },
                )
            }
        }
    }
}

/** 빈 목록은 안내 문구만 둔다 — 만들기 진입은 FAB 이 맡는다. */
@Composable
private fun EmptyStories(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.studio_stories_empty),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

private const val PENDING_BANNER_KEY = "pending-banner"

@Preview(showBackground = true, name = "제작 · 목록")
@Composable
private fun StudioScreenPreview() {
    ManyakTheme(darkTheme = false) {
        StudioContent(
            state = StudioUiState(isLoading = false, stories = previewStories()),
            contentPadding = PaddingValues(0.dp),
            onOpenStory = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "제작 · 빈 목록")
@Composable
private fun StudioScreenEmptyPreview() {
    ManyakTheme(darkTheme = false) {
        StudioContent(
            state = StudioUiState(isLoading = false),
            contentPadding = PaddingValues(0.dp),
            onOpenStory = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "제작 · 이어서 만들기 배너")
@Composable
private fun StudioScreenPendingBannerPreview() {
    ManyakTheme(darkTheme = false) {
        StudioContent(
            state =
                StudioUiState(
                    isLoading = false,
                    stories = previewStories(),
                    pendingBanner =
                        PendingCreationBanner(
                            isCompleting = false,
                            resumePoint = CreationResumePoint.StorylineStep,
                        ),
                ),
            contentPadding = PaddingValues(0.dp),
            onOpenStory = {},
            onIntent = {},
        )
    }
}

private fun previewStories(): List<StorySummary> =
    listOf(
        previewStory(
            id = "1",
            title = "두 번째 시계공",
            oneLineIntro = "멈춘 시계탑을 고치는 견습공의 하루",
            genres = listOf("판타지", "미스터리"),
            turnCount = 1_284,
            createdDate = "2026-08-03",
        ),
        previewStory(
            id = "2",
            title = "달빛 아래의 계약",
            oneLineIntro = "보름달이 뜨는 밤에만 열리는 상점",
            genres = listOf("로맨스", "판타지", "미스터리", "스릴러", "코미디"),
            turnCount = 312,
            createdDate = "2026-07-21",
        ),
        previewStory(
            id = "3",
            title = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            oneLineIntro = "아주 긴 한 줄 소개도 마찬가지로 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            genres = listOf("일상"),
            turnCount = 7,
            createdDate = "2026-06-30",
        ),
        previewStory(
            id = "4",
            title = "잊힌 등대",
            oneLineIntro = "",
            genres = emptyList(),
            turnCount = 0,
            createdDate = null,
        ),
    )

@Suppress("LongParameterList")
private fun previewStory(
    id: String,
    title: String,
    oneLineIntro: String,
    genres: List<String>,
    turnCount: Long,
    createdDate: String?,
): StorySummary =
    StorySummary(
        id = id,
        title = title,
        authorNickname = null,
        thumbnailUrl = null,
        oneLineIntro = oneLineIntro,
        genres = genres,
        turnCount = turnCount,
        createdDate = createdDate,
    )
