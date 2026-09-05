package app.manyak.feature.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.entity.story.StoryTagCategory
import app.manyak.core.ui.R
import app.manyak.core.ui.component.FocusScrollMargin
import app.manyak.core.ui.component.ScrollEdgeFade
import app.manyak.core.ui.component.clearFocusOnTap
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 키워드 단계 뒤로가기는 생성 전 퍼널 이탈이다. 임시 저장하지 않은 입력이 있으면 경고를 거치고,
 * 없으면 조용히 나간다. 스토리라인 단계가 이 목적지를 대체하므로 생성 후 이탈 처리는 그 화면이
 * 소유하고, 여기서는 방어적으로 스토어 정리(진행 중 레코드 우선 판정 포함)만 거친다.
 */
@Composable
fun CreateKeywordScreen(
    onLeaveFunnel: () -> Unit,
    onOpenStorylineStep: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateKeywordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenStorylineStep by rememberUpdatedState(onOpenStorylineStep)
    val currentOnLeaveFunnel by rememberUpdatedState(onLeaveFunnel)
    val lifecycleOwner = LocalLifecycleOwner.current

    // 시스템 뒤로가기도 헤더 닫기와 같은 이탈 처리를 거친다.
    BackHandler { viewModel.onIntent(CreateKeywordIntent.LeaveFunnel) }

    SaveDraftWhenBackgrounded { viewModel.onIntent(CreateKeywordIntent.SaveDraft) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    CreateKeywordEffect.NavigateToStoryline -> currentOnOpenStorylineStep()

                    is CreateKeywordEffect.ExitFunnel -> currentOnLeaveFunnel()
                }
            }
        }
    }

    CreateKeywordContent(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )

    state.exitWarning?.let { warning ->
        FunnelExitWarningDialog(
            warning = warning,
            onConfirmLeave = { viewModel.onIntent(CreateKeywordIntent.ConfirmLeaveFunnel) },
            onDismiss = { viewModel.onIntent(CreateKeywordIntent.DismissExitWarning) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateKeywordContent(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addKeywordTarget by rememberSaveable(stateSaver = KeywordTargetSaver) { mutableStateOf<KeywordTarget?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current

    FocusScrollMargin {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .clearFocusOnTap(focusManager),
        ) {
            CreateFunnelHeader(
                draftSave = state.draftSave,
                onSaveDraft = { onIntent(CreateKeywordIntent.SaveDraft) },
                onClose = { onIntent(CreateKeywordIntent.LeaveFunnel) },
            )
            CreateStepIndicator(
                currentStep = 0,
                stepNameRes = R.string.create_step_keyword,
            )
            // 스크롤 본문이 푸터 경계에서 딱 잘리므로 바닥에 페이드를 겹친다.
            Box(modifier = Modifier.weight(1f)) {
                KeywordStepBody(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onIntent = onIntent,
                    onOpenAddKeyword = { target -> addKeywordTarget = target },
                )
                ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
            }
            // IME가 열리면 CTA 영역을 콘텐츠에 돌려 입력 필드가 키보드 위로 스크롤되게 한다.
            if (!imeVisible) {
                CreateKeywordFooter(state = state, onIntent = onIntent)
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeywordStepBody(
    state: CreateKeywordUiState,
    onIntent: (CreateKeywordIntent) -> Unit,
    onOpenAddKeyword: (KeywordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // 복원 결과를 기다리는 동안은 본문을 비워 둔다 — 저장해 둔 키워드가 있는지
        // 모르는 채로 빈 입력 화면을 그리면 재개 진입에서 화면이 번쩍인다.
        state.isRestoring -> Spacer(modifier = modifier)

        state.providedTags is ProvidedTags.Failed ->
            CreateKeywordFailureContent(modifier = modifier, state = state, onIntent = onIntent)

        else ->
            LazyColumn(modifier = modifier) {
                item { KeywordStepTitle() }
                stickyHeader {
                    CategoryTabs(state = state, onIntent = onIntent)
                }
                item {
                    CategoryContent(
                        state = state,
                        onIntent = onIntent,
                        onOpenAddKeyword = onOpenAddKeyword,
                    )
                }
                item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.gutter)) }
            }
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

    val footerErrorRes =
        when {
            !state.isFooterEnabled -> null
            state.validationErrorCategory == state.activeCategory -> R.string.create_error_select_keyword
            state.showDuplicateNameFooterError -> R.string.create_error_duplicate_name_footer
            else -> null
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                // 위 여백은 오류 문구가 있을 때만 둔다 — 문구가 없으면 콘텐츠와 버튼 사이는
                // 페이드가 맡으므로 빈 공간을 더할 이유가 없다.
                .padding(
                    top = if (footerErrorRes == null) 0.dp else ManyakTheme.spacing.compact,
                    bottom = ManyakTheme.spacing.gutter,
                ),
    ) {
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
            isGenerating = state.isGeneratingStorylines,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun FooterButtons(
    isFirstCategory: Boolean,
    isLastCategory: Boolean,
    enabled: Boolean,
    isGenerating: Boolean,
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
            loading = isLastCategory && isGenerating,
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

/**
 * 어느 대상에 키워드를 더하던 중인지를 재생성 너머로 남긴다. 남기지 않으면 입력하던 시트가
 * 회전 한 번에 닫히고, 시트 안의 입력값도 함께 버려진다.
 *
 * 값이 셋뿐이라 [KeywordTarget] 에 직렬화를 붙이는 대신 여기서 좁게 변환한다.
 */
private val KeywordTargetSaver: Saver<KeywordTarget?, String> =
    Saver(
        save = { target ->
            when (target) {
                null -> null
                KeywordTarget.Genre -> SAVED_TARGET_GENRE
                KeywordTarget.Protagonist -> SAVED_TARGET_PROTAGONIST
                is KeywordTarget.Supporting -> SAVED_TARGET_SUPPORTING_PREFIX + target.characterId
            }
        },
        restore = { saved ->
            when {
                saved == SAVED_TARGET_GENRE -> KeywordTarget.Genre
                saved == SAVED_TARGET_PROTAGONIST -> KeywordTarget.Protagonist
                saved.startsWith(SAVED_TARGET_SUPPORTING_PREFIX) ->
                    saved
                        .removePrefix(SAVED_TARGET_SUPPORTING_PREFIX)
                        .toLongOrNull()
                        ?.let(KeywordTarget::Supporting)

                else -> null
            }
        },
    )

private const val SAVED_TARGET_GENRE = "genre"
private const val SAVED_TARGET_PROTAGONIST = "protagonist"
private const val SAVED_TARGET_SUPPORTING_PREFIX = "supporting:"

@Preview(showBackground = true, name = "키워드 선택 · 라이트")
@Composable
private fun CreateKeywordScreenPreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state = previewKeywordState(),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "키워드 선택 · 검증 오류")
@Composable
private fun CreateKeywordScreenValidationErrorPreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state = previewKeywordState().copy(validationErrorCategory = StoryTagCategory.GENRE),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "키워드 선택 · 태그 로드 오류")
@Composable
private fun CreateKeywordScreenTagsLoadFailurePreview() {
    ManyakTheme(darkTheme = false) {
        CreateKeywordContent(
            state = CreateKeywordUiState(isRestoring = false, providedTags = ProvidedTags.Failed),
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
                previewKeywordState().copy(
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
            onIntent = {},
        )
    }
}
