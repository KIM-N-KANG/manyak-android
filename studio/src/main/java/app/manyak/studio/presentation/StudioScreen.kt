package app.manyak.studio.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.withRowListMargins
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.studio.presentation.component.CreateStoryFab
import app.manyak.studio.presentation.component.CreationProgressCard
import app.manyak.studio.presentation.component.CreationProgressCardKind
import app.manyak.studio.presentation.component.MyStoriesSkeleton
import app.manyak.studio.presentation.component.MyStoryCard
import app.manyak.studio.presentation.component.StudioDialogs
import app.manyak.common.R as CommonR
import app.manyak.report.R as ReportR
import app.manyak.studio.R as StudioR

/**
 * 제작 탭(내가 만든 스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로 목록에는 `Modifier.padding` 이 아니라
 * 목록의 `contentPadding` 으로 넘긴다 — 그래야 콘텐츠가 헤더 아래로 흘러 들어간다.
 *
 * 제작 퍼널 진입 FAB 과 초안·완성 요청 카드는 셸이 아니라 이 화면이 소유한다. 초안이 있으면
 * 목록 맨 위에 초안 카드를 두고, FAB 등 카드가 아닌 경로의 진입은 새로 만들기 다이얼로그로 묻는다.
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
                        Toast.makeText(context, CommonR.string.studio_story_deleted, Toast.LENGTH_SHORT).show()

                    StudioEffect.ShowStoryDeleteFailed ->
                        Toast.makeText(context, CommonR.string.studio_story_delete_failed, Toast.LENGTH_SHORT).show()

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
            // 로컬 카드가 하나라도 있으면 서버 목록이 없거나 실패해도 목록으로 그린다 — 그래야 스크롤과
            // 당겨서 새로고침이 살아 완성을 확인할 수 있다.
            state.hasLocalCards ->
                MyStories(
                    state = state,
                    contentPadding = contentPadding,
                    onOpenStory = onOpenStory,
                    onIntent = onIntent,
                )

            state.isLoading ->
                StoriesStatus(contentPadding = contentPadding) {
                    if (showSkeleton) {
                        MyStoriesSkeleton()
                    }
                    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
                }

            state.loadFailed ->
                StoriesStatus(contentPadding = contentPadding) {
                    LoadFailedContent(
                        message = stringResource(CommonR.string.story_load_failed),
                        onRetry = { onIntent(StudioIntent.Retry) },
                        modifier = Modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
                    )
                }

            state.stories.isEmpty() ->
                StoriesStatus(contentPadding = contentPadding) {
                    EmptyStories(modifier = Modifier.fillMaxSize())
                }

            else ->
                MyStories(
                    state = state,
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

/** 목록이 없는 상태(조회 중·실패·빈 목록)의 자리. 스크롤할 것이 없으므로 chrome 여백을 화면에 씌운다. */
@Composable
private fun StoriesStatus(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        content()
    }
}

/**
 * 초안 → 완성 요청(제출 최신순) → 완성된 스토리 순의 한 목록. 키는 종류별 접두사로 나눠 로컬 카드와
 * 서버 스토리가 겹치지 않는다. 서버 목록 조회가 실패했으면 로컬 카드 아래에 재시도를 둔다.
 */
@Composable
private fun MyStories(
    state: StudioUiState,
    contentPadding: PaddingValues,
    onOpenStory: (String) -> Unit,
    onIntent: (StudioIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalAnalytics.current
    val impressions = rememberImpressionTracker()
    ManyakPullToRefreshBox(
        modifier = modifier,
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(StudioIntent.Refresh) },
        contentPadding = contentPadding,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 좌우 여백은 카드가 스스로 갖는다 — 채팅 목록과 같은 리듬이다.
            contentPadding = contentPadding.withRowListMargins(),
        ) {
            state.draft?.let {
                item(key = DRAFT_KEY) {
                    CreationProgressCard(
                        kind = CreationProgressCardKind.Draft,
                        onPrimaryAction = { onIntent(StudioIntent.ResumeCreation) },
                        onOptionsClick = { onIntent(StudioIntent.OpenCardOptions(StudioCard.Draft)) },
                    )
                }
            }
            items(state.completionRequests, key = { request ->
                "$COMPLETION_KEY_PREFIX${request.requestId}"
            }) { request ->
                CompletionRequestRow(request = request, onOpenStory = onOpenStory, onIntent = onIntent)
            }
            itemsIndexed(state.stories, key = { _, story -> "$STORY_KEY_PREFIX${story.id}" }) { index, story ->
                MyStoryCard(
                    story = story,
                    onClick = {
                        analytics.track(AnalyticsEvent.StoryCardClicked(story.id, index, StoryListSection.CREATED))
                        onOpenStory(story.id)
                    },
                    onOptionsClick = { onIntent(StudioIntent.OpenCardOptions(StudioCard.Story(story))) },
                    modifier =
                        Modifier.trackImpression(impressions, key = story.id) {
                            analytics.track(
                                AnalyticsEvent.StoryCardImpressed(story.id, index, StoryListSection.CREATED),
                            )
                        },
                )
            }
            if (state.loadFailed) {
                item(key = LOAD_FAILED_KEY) {
                    LoadFailedContent(
                        message = stringResource(CommonR.string.story_load_failed),
                        onRetry = { onIntent(StudioIntent.Retry) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.block),
                    )
                }
            }
        }
    }
}

/** 요청 상태별 카드. 완성 중에는 아무 동작도 없고, 완료·실패만 각각 상세 진입과 재시도·삭제를 연다. */
@Composable
private fun CompletionRequestRow(
    request: CompletionRequestSummary,
    onOpenStory: (String) -> Unit,
    onIntent: (StudioIntent) -> Unit,
) {
    when (request.status) {
        CompletionRequestStatus.PENDING -> CreationProgressCard(kind = CreationProgressCardKind.Completing)

        CompletionRequestStatus.COMPLETED ->
            CreationProgressCard(
                kind = CreationProgressCardKind.Completed(request.storyTitle.orEmpty()),
                onClick = request.storyId?.let { storyId -> { onOpenStory(storyId) } },
            )

        CompletionRequestStatus.FAILED ->
            CreationProgressCard(
                kind = CreationProgressCardKind.Failed,
                onPrimaryAction = { onIntent(StudioIntent.RetryCompletion(request.requestId)) },
                onOptionsClick = {
                    onIntent(StudioIntent.OpenCardOptions(StudioCard.FailedRequest(request.requestId)))
                },
            )
    }
}

/** 빈 목록은 안내 문구만 둔다 — 만들기 진입은 FAB 이 맡는다. */
@Composable
private fun EmptyStories(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(StudioR.string.studio_stories_empty),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

private const val DRAFT_KEY = "draft"
private const val COMPLETION_KEY_PREFIX = "completion:"
private const val STORY_KEY_PREFIX = "story:"
private const val LOAD_FAILED_KEY = "load-failed"

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

@Preview(showBackground = true, name = "제작 · 초안·완성 중 카드")
@Composable
private fun StudioScreenProgressCardsPreview() {
    ManyakTheme(darkTheme = false) {
        StudioContent(
            state =
                StudioUiState(
                    isLoading = false,
                    stories = previewStories(),
                    draft = CreationProgressSummary(CreationStage.STORY_DRAFT, CreationResumePoint.StorylineStep),
                    completionRequests =
                        listOf(
                            CompletionRequestSummary(
                                "req-1",
                                submittedAt = 2,
                                status = CompletionRequestStatus.PENDING,
                            ),
                            CompletionRequestSummary("req-2", submittedAt = 1, status = CompletionRequestStatus.FAILED),
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
            likeCount = 312,
            turnCount = 1_284,
            createdDate = "2026-08-03",
        ),
        previewStory(
            id = "2",
            title = "달빛 아래의 계약",
            oneLineIntro = "보름달이 뜨는 밤에만 열리는 상점",
            genres = listOf("로맨스", "판타지", "미스터리", "스릴러", "코미디"),
            likeCount = 48,
            turnCount = 312,
            createdDate = "2026-07-21",
        ),
        previewStory(
            id = "3",
            title = "아주 긴 제목은 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            oneLineIntro = "아주 긴 한 줄 소개도 마찬가지로 한 줄에서 잘려 카드 높이를 흔들지 않는다",
            genres = listOf("일상"),
            likeCount = 1,
            turnCount = 7,
            createdDate = "2026-06-30",
        ),
        previewStory(
            id = "4",
            title = "잊힌 등대",
            oneLineIntro = "",
            genres = emptyList(),
            likeCount = 0,
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
    likeCount: Long,
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
        likeCount = likeCount,
        turnCount = turnCount,
        createdDate = createdDate,
    )
