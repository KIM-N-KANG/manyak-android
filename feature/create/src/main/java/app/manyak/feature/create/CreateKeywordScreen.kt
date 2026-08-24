package app.manyak.feature.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 키워드 단계는 이탈 가드 없이 퍼널 진입 전 화면으로 돌아간다. */
@Composable
fun CreateKeywordScreen(
    onLeaveFunnel: () -> Unit,
    onOpenStorylineStep: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateKeywordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenStorylineStep by rememberUpdatedState(onOpenStorylineStep)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    CreateKeywordEffect.NavigateToStoryline -> currentOnOpenStorylineStep()
                }
            }
        }
    }

    CreateKeywordContent(
        state = state,
        onBack = onLeaveFunnel,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateKeywordContent(
    state: CreateKeywordUiState,
    onBack: () -> Unit,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addKeywordTarget by remember { mutableStateOf<KeywordTarget?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
    ) {
        CreateFunnelHeader(onBack = onBack)
        CreateStepIndicator(
            currentStep = 0,
            stepNameRes = R.string.create_step_keyword,
        )
        if (state.providedTags is ProvidedTags.Failed) {
            CreateKeywordFailureContent(
                modifier = Modifier.weight(1f),
                state = state,
                onIntent = onIntent,
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item { KeywordStepTitle() }
                stickyHeader {
                    CategoryTabs(state = state, onIntent = onIntent)
                }
                item {
                    CategoryContent(
                        state = state,
                        onIntent = onIntent,
                        onOpenAddKeyword = { target -> addKeywordTarget = target },
                    )
                }
                item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom)) }
            }
        }
        // IME가 열리면 CTA 영역을 콘텐츠에 돌려 입력 필드가 키보드 위로 스크롤되게 한다.
        if (!imeVisible) {
            CreateKeywordFooter(state = state, onIntent = onIntent)
        }
    }

    addKeywordTarget?.let { target ->
        AddKeywordDialog(
            categoryLabel = stringResource(target.category.labelRes),
            placeholder = stringResource(target.category.addKeywordPlaceholderRes),
            onDismiss = { addKeywordTarget = null },
            onSubmit = { name ->
                onIntent(CreateKeywordIntent.AddCustomTag(target, name))
                addKeywordTarget = null
            },
        )
    }
}

@Composable
private fun CreateKeywordFailureContent(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        KeywordStepTitle()
        CategoryTabs(state = state, onIntent = onIntent)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            TagsLoadFailure(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = ManyakTheme.spacing.gutter),
                onRetry = { onIntent(CreateKeywordIntent.RetryTags) },
            )
        }
    }
}

@Composable
private fun KeywordStepTitle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.create_keyword_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.create_keyword_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 필수 입력 검증 실패는 오류를 노출하고, 태그 조회 실패는 CTA를 비활성화한다. */
@Composable
private fun CreateKeywordFooter(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFirstCategory = state.activeCategory.previous == null
    val isLastCategory = state.activeCategory.next == null

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.compact, bottom = ManyakTheme.spacing.gutter),
    ) {
        val footerErrorRes =
            when {
                !state.isFooterEnabled -> null
                state.validationErrorCategory == state.activeCategory -> R.string.create_error_select_keyword
                state.showDuplicateNameFooterError -> R.string.create_error_duplicate_name_footer
                else -> null
            }
        if (footerErrorRes != null) {
            Text(
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.compact),
                text = stringResource(footerErrorRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textDanger,
            )
        }
        FooterButtons(
            isFirstCategory = isFirstCategory,
            isLastCategory = isLastCategory,
            enabled = state.isFooterEnabled,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun FooterButtons(
    isFirstCategory: Boolean,
    isLastCategory: Boolean,
    enabled: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        if (!isFirstCategory) {
            FunnelNeutralButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.create_cta_previous),
                enabled = enabled,
                onClick = { onIntent(CreateKeywordIntent.GoPrevious) },
            )
        }
        val primaryLabelRes =
            if (isLastCategory) R.string.create_cta_generate_storylines else R.string.create_cta_next
        FunnelPrimaryButton(
            modifier = Modifier.weight(1f),
            label = stringResource(primaryLabelRes),
            enabled = enabled,
            onClick = {
                val intent =
                    if (isLastCategory) {
                        CreateKeywordIntent.GenerateStorylines
                    } else {
                        CreateKeywordIntent.GoNext
                    }
                onIntent(intent)
            },
        )
    }
}

private fun previewState(): CreateKeywordUiState =
    CreateKeywordUiState(
        providedTags =
            ProvidedTags.Loaded(
                mapOf(
                    StoryTagCategory.GENRE to
                        listOf(
                            StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE),
                            StoryTag(id = 2, name = "판타지", category = StoryTagCategory.GENRE),
                            StoryTag(id = 3, name = "미스터리", category = StoryTagCategory.GENRE),
                        ),
                ),
            ),
    )

@Preview(showBackground = true, name = "키워드 선택 · 라이트")
@Composable
private fun CreateKeywordScreenPreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state = previewState(),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "키워드 선택 · 검증 오류")
@Composable
private fun CreateKeywordScreenValidationErrorPreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state = previewState().copy(validationErrorCategory = StoryTagCategory.GENRE),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "키워드 선택 · 태그 로드 오류")
@Composable
private fun CreateKeywordScreenTagsLoadFailurePreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state = CreateKeywordUiState(providedTags = ProvidedTags.Failed),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "키워드 선택 · 주변 인물")
@Composable
private fun CreateKeywordSupportingCharactersPreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state =
                previewState().copy(
                    activeCategory = StoryTagCategory.SUPPORTING_CHARACTER,
                    selectedGenreTagIds = setOf(1),
                    protagonist =
                        KeywordCharacter(
                            id = CreateKeywordUiState.PROTAGONIST_ID,
                            selectedTagIds = setOf(1),
                        ),
                    supportingCharacters =
                        listOf(
                            KeywordCharacter(
                                id = CreateKeywordUiState.FIRST_SUPPORTING_ID,
                                name = "한도윤",
                            ),
                            KeywordCharacter(id = CreateKeywordUiState.FIRST_SUPPORTING_ID + 1),
                        ),
                ),
            onBack = {},
            onIntent = {},
        )
    }
}
