package app.manyak.feature.create

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.entity.story.StorylineRating
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ScrollEdgeFade
import app.manyak.designsystem.text.storyAnnotatedString
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 스토리라인 선택 단계. 이 목적지는 키워드 목적지를 대체하므로 뒤로가기는 홈 복귀(퍼널 이탈)다.
 * 이탈 시 내용이 남으면 임시 저장(또는 진행 중 레코드 유지) 후 토스트를 띄우고, 보존할 것이 없으면
 * 소실 경고 다이얼로그를 거친다(3-1 이탈 가드).
 */
@Composable
fun CreateStorylineScreen(
    onLeaveFunnel: () -> Unit,
    onOpenAdditionalInfoStep: (storylineIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateStorylineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draftSave by viewModel.draftSave.collectAsStateWithLifecycle()
    val currentOnOpenAdditionalInfoStep by rememberUpdatedState(onOpenAdditionalInfoStep)
    val currentOnLeaveFunnel by rememberUpdatedState(onLeaveFunnel)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // 시스템 뒤로가기도 헤더 닫기와 같은 이탈 처리를 거친다.
    BackHandler { viewModel.onIntent(CreateStorylineIntent.LeaveFunnel) }

    SaveDraftWhenBackgrounded { viewModel.onIntent(CreateStorylineIntent.SaveDraft) }

    // 응답을 못 받은 생성 요청의 복구 폴링. STARTED 동안만 돌아 백그라운드에서 멈추고 복귀 시 재개된다.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.driveRecovery()
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is CreateStorylineEffect.NavigateToAdditionalInfo ->
                        currentOnOpenAdditionalInfoStep(effect.storylineIndex)

                    CreateStorylineEffect.ShowRatingSyncFailed ->
                        Toast
                            .makeText(context, R.string.create_storyline_rating_sync_failed, Toast.LENGTH_SHORT)
                            .show()

                    is CreateStorylineEffect.ExitFunnel -> currentOnLeaveFunnel()
                }
            }
        }
    }

    CreateStorylineContent(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        draftSave = draftSave,
    )

    state.exitWarning?.let { warning ->
        FunnelExitWarningDialog(
            warning = warning,
            onConfirmLeave = { viewModel.onIntent(CreateStorylineIntent.ConfirmLeaveFunnel) },
            onDismiss = { viewModel.onIntent(CreateStorylineIntent.DismissExitWarning) },
        )
    }
}

@Composable
private fun CreateStorylineContent(
    state: CreateStorylineUiState,
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
    draftSave: DraftSaveUiState = DraftSaveUiState(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        CreateFunnelHeader(
            draftSave = draftSave,
            onSaveDraft = { onIntent(CreateStorylineIntent.SaveDraft) },
            onClose = { onIntent(CreateStorylineIntent.LeaveFunnel) },
        )
        CreateStepIndicator(
            currentStep = 1,
            stepNameRes = R.string.create_step_storyline,
        )
        when (state.content) {
            // 복원 결과를 기다리는 동안은 본문을 비워 둔다 — 로딩과 결과 중 무엇을 그려도 곧
            // 다른 쪽으로 바뀌어 재개 진입에서 화면이 번쩍인다.
            StorylineContent.Restoring -> Spacer(modifier = Modifier.weight(1f))

            StorylineContent.Generating -> StorylineGeneratingContent(modifier = Modifier.weight(1f))

            is StorylineContent.Loaded -> {
                // 스크롤 본문이 푸터 경계에서 딱 잘리므로 바닥에 페이드를 겹친다.
                Box(modifier = Modifier.weight(1f)) {
                    StorylineResultContent(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        onIntent = onIntent,
                    )
                    ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
                }
                CreateStorylineFooter(
                    hasStoryline = state.activeStoryline != null,
                    showKeywordsTrigger = state.hasKeywords,
                    onIntent = onIntent,
                )
            }
        }
    }

    SelectedKeywordsSheet(
        keywords = state.selectedKeywords,
        onDismiss = { onIntent(CreateStorylineIntent.DismissSelectedKeywords) },
        onRetry = { onIntent(CreateStorylineIntent.ShowSelectedKeywords) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorylineResultContent(
    state: CreateStorylineUiState,
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 다른 스토리라인으로 전환하면 본문을 처음부터 읽도록 스크롤을 되돌린다.
    //
    // 직전 값과 비교하는 이유는 이 효과가 **컴포지션에 들어올 때마다** 실행되기 때문이다. 구성 변경으로
    // 화면이 다시 만들어지면 탭은 그대로인데도 효과가 다시 돌아 `listState` 가 방금 복원한 위치를
    // 0 으로 덮어쓴다. 재생성 직후에는 직전 값이 현재 값으로 초기화되어 되돌리기가 일어나지 않는다.
    var previousIndex by remember { mutableIntStateOf(state.activeIndex) }

    LaunchedEffect(state.activeIndex) {
        val switched = state.activeIndex != previousIndex
        previousIndex = state.activeIndex
        if (switched) listState.scrollToItem(0)
    }

    LazyColumn(modifier = modifier, state = listState) {
        item { StorylineStepTitle() }
        stickyHeader {
            StorylineTabs(state = state, onIntent = onIntent)
        }
        state.activeStoryline?.let { storyline ->
            item {
                StorylineBody(
                    text = storyline.storyline,
                    rating = state.activeRating,
                    onToggleRating = { rating -> onIntent(CreateStorylineIntent.ToggleRating(rating)) },
                )
            }
        }
        when {
            state.hasGenerationError ->
                item {
                    StorylineNotice(
                        text =
                            stringResource(
                                if (state.storylines.isEmpty()) {
                                    R.string.create_storyline_error_generate
                                } else {
                                    R.string.create_storyline_error_regenerate
                                },
                            ),
                        isError = true,
                    )
                }

            state.storylines.isEmpty() ->
                item { StorylineNotice(text = stringResource(R.string.create_storyline_empty), isError = false) }
        }
        item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.gutter)) }
    }
}

@Composable
private fun StorylineStepTitle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.create_storyline_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.create_storyline_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 실패 인라인 오류와 빈 결과 안내. 재시도는 푸터의 "다시 만들기"가 담당한다. */
@Composable
private fun StorylineNotice(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter),
        text = text,
        style = ManyakTheme.typography.bodyMedium,
        color = if (isError) ManyakTheme.colors.textDanger else ManyakTheme.colors.textSubtle,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorylineTabs(
    state: CreateStorylineUiState,
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = stringArrayResource(R.array.create_storyline_tab_labels)
    val tabCount = if (state.storylines.isEmpty()) EXPECTED_STORYLINE_COUNT else state.storylines.size
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SecondaryTabRow(
            modifier = modifier.fillMaxWidth(),
            selectedTabIndex = state.activeIndex,
            containerColor = ManyakTheme.colors.surface,
            contentColor = ManyakTheme.colors.text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(state.activeIndex),
                    height = 1.5.dp,
                    color = ManyakTheme.colors.text,
                )
            },
        ) {
            repeat(tabCount) { index ->
                val selected = index == state.activeIndex
                val enabled = index in state.storylines.indices
                Tab(
                    selected = selected,
                    enabled = enabled,
                    onClick = { onIntent(CreateStorylineIntent.SelectStoryline(index)) },
                    text = {
                        Text(
                            text = labels.getOrElse(index) { (index + 1).toString() },
                            style = ManyakTheme.typography.labelLarge,
                            color =
                                when {
                                    !enabled -> ManyakTheme.colors.textDisabled
                                    selected -> ManyakTheme.colors.text
                                    else -> ManyakTheme.colors.textSubtle
                                },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StorylineBody(
    text: String,
    rating: StorylineRating?,
    onToggleRating: (StorylineRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Text(
            text = storyAnnotatedString(text),
            style = ManyakTheme.typography.bodyReading,
            color = ManyakTheme.colors.text,
        )
        StorylineRatingButtons(
            modifier = Modifier.align(Alignment.End),
            rating = rating,
            onToggle = onToggleRating,
        )
    }
}

@Composable
private fun CreateStorylineFooter(
    hasStoryline: Boolean,
    showKeywordsTrigger: Boolean,
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        if (showKeywordsTrigger) {
            SelectedKeywordsTrigger(onClick = { onIntent(CreateStorylineIntent.ShowSelectedKeywords) })
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    // 이 푸터에는 오류 문구가 없어 위를 띄우지 않는다 — 콘텐츠와의 경계는 페이드가 맡는다.
                    .padding(bottom = ManyakTheme.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            FunnelNeutralButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.create_cta_regenerate),
                enabled = true,
                onClick = { onIntent(CreateStorylineIntent.Regenerate) },
            )
            FunnelPrimaryButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.create_cta_select_storyline),
                enabled = hasStoryline,
                onClick = { onIntent(CreateStorylineIntent.ConfirmSelection) },
            )
        }
    }
}

/** 서버 계약상 생성 결과는 3개다. 결과가 오기 전 탭 자리도 이 수만큼 그린다. */
private const val EXPECTED_STORYLINE_COUNT = 3

@Preview(showBackground = true, name = "스토리라인 선택 · 라이트")
@Composable
private fun CreateStorylineScreenPreview() {
    ManyakTheme(darkTheme = false) {
        CreateStorylineContent(
            state = CreateStorylineUiState(content = StorylineContent.Loaded(previewStorylines())),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "스토리라인 선택 · 평가 활성")
@Composable
private fun CreateStorylineScreenRatedPreview() {
    ManyakTheme(darkTheme = false) {
        CreateStorylineContent(
            state =
                CreateStorylineUiState(
                    content = StorylineContent.Loaded(previewStorylines()),
                    activeIndex = 1,
                    ratings = mapOf(2L to StorylineRating.GOOD),
                ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "스토리라인 선택 · 생성 실패")
@Composable
private fun CreateStorylineScreenFailedPreview() {
    ManyakTheme(darkTheme = false) {
        CreateStorylineContent(
            state =
                CreateStorylineUiState(
                    content = StorylineContent.Loaded(emptyList()),
                    hasGenerationError = true,
                ),
            onIntent = {},
        )
    }
}
