package app.manyak.feature.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 스토리라인 선택 단계. 뒤로가기는 키워드 단계 복귀이며 키워드 입력은 백스택에 남아 유지된다. */
@Composable
fun CreateStorylineScreen(
    onBack: () -> Unit,
    onOpenAdditionalInfoStep: (storylineIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateStorylineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenAdditionalInfoStep by rememberUpdatedState(onOpenAdditionalInfoStep)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is CreateStorylineEffect.NavigateToAdditionalInfo ->
                        currentOnOpenAdditionalInfoStep(effect.storylineIndex)
                }
            }
        }
    }

    CreateStorylineContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreateStorylineContent(
    state: CreateStorylineUiState,
    onBack: () -> Unit,
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 다른 스토리라인으로 전환하면 본문을 처음부터 읽도록 스크롤을 되돌린다.
    LaunchedEffect(state.activeIndex) {
        listState.scrollToItem(0)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        CreateFunnelHeader(onBack = onBack)
        CreateStepIndicator(
            currentStep = 1,
            stepNameRes = R.string.create_step_storyline,
        )
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            item { StorylineStepTitle() }
            stickyHeader {
                StorylineTabs(state = state, onIntent = onIntent)
            }
            item {
                StorylineBody(
                    text = state.activeStoryline.orEmpty(),
                    rating = state.activeRating,
                    onToggleRating = { rating -> onIntent(CreateStorylineIntent.ToggleRating(rating)) },
                )
            }
            item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom)) }
        }
        CreateStorylineFooter(hasStoryline = state.activeStoryline != null, onIntent = onIntent)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorylineTabs(
    state: CreateStorylineUiState,
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = stringArrayResource(R.array.create_storyline_tab_labels)
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
            state.storylines.forEachIndexed { index, _ ->
                val selected = index == state.activeIndex
                Tab(
                    selected = selected,
                    onClick = { onIntent(CreateStorylineIntent.SelectStoryline(index)) },
                    text = {
                        Text(
                            text = labels.getOrElse(index) { (index + 1).toString() },
                            style = ManyakTheme.typography.labelLarge,
                            color = if (selected) ManyakTheme.colors.text else ManyakTheme.colors.textSubtle,
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
                .padding(ManyakTheme.spacing.gutter),
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
    onIntent: (CreateStorylineIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.compact, bottom = ManyakTheme.spacing.gutter),
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

@Preview(showBackground = true, name = "스토리라인 선택 · 라이트")
@Composable
private fun CreateStorylineScreenPreview() {
    ManyakTheme(darkTheme = false) {
        CreateStorylineContent(
            state = CreateStorylineUiState(storylines = CreateStorylineViewModel.PLACEHOLDER_STORYLINES),
            onBack = {},
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
                    storylines = CreateStorylineViewModel.PLACEHOLDER_STORYLINES,
                    activeIndex = 1,
                    ratings = mapOf(1 to StorylineRating.GOOD),
                ),
            onBack = {},
            onIntent = {},
        )
    }
}
