package app.manyak.feature.create

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 추가 정보 단계. 앱 바 닫기와 디바이스 뒤로가기는 퍼널 이탈(홈 복귀)이고, 스토리라인
 * 단계로 되돌아가는 수단은 하단 "다시 선택하기" 하나뿐이다 — 이탈과 단계 복귀가 같은 제스처를
 * 쓰면 어느 쪽인지 알 수 없다.
 */
@Composable
fun CreateAdditionalInfoScreen(
    storylineIndex: Int,
    onLeaveFunnel: () -> Unit,
    onBackToStoryline: () -> Unit,
    onEnterChat: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateAdditionalInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draftSaveStatus by viewModel.draftSaveStatus.collectAsStateWithLifecycle()
    val currentOnEnterChat by rememberUpdatedState(onEnterChat)
    val currentOnLeaveFunnel by rememberUpdatedState(onLeaveFunnel)
    val currentOnBackToStoryline by rememberUpdatedState(onBackToStoryline)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // 디바이스 뒤로가기도 앱 바 닫기와 같은 이탈 처리를 거친다.
    BackHandler { viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel) }

    // 응답을 못 받았거나 409 로 거절된 완성 요청의 복구 폴링. STARTED 동안만 돌아 백그라운드에서
    // 멈추고 복귀 시 재개된다.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.driveCompletionRecovery()
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is CreateAdditionalInfoEffect.EnterChatAfterCompletion -> {
                        Toast
                            .makeText(context, R.string.create_story_completed, Toast.LENGTH_SHORT)
                            .show()
                        currentOnEnterChat(effect.chatId)
                    }

                    is CreateAdditionalInfoEffect.ExitFunnel -> currentOnLeaveFunnel()

                    CreateAdditionalInfoEffect.NavigateBackToStoryline -> currentOnBackToStoryline()
                }
            }
        }
    }

    CreateAdditionalInfoContent(
        storylineIndex = storylineIndex,
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        draftSaveStatus = draftSaveStatus,
    )

    if (state.showExitWarningDialog) {
        ExitWarningDialog(
            onConfirmLeave = { viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmLeaveFunnel) },
            onDismiss = { viewModel.onIntent(CreateAdditionalInfoIntent.DismissExitWarning) },
        )
    }

    if (state.showReselectWarningDialog) {
        ReselectWarningDialog(
            onConfirmReselect = { viewModel.onIntent(CreateAdditionalInfoIntent.ConfirmReselect) },
            onDismiss = { viewModel.onIntent(CreateAdditionalInfoIntent.DismissReselectWarning) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAdditionalInfoContent(
    storylineIndex: Int,
    state: CreateAdditionalInfoUiState,
    onIntent: (CreateAdditionalInfoIntent) -> Unit,
    modifier: Modifier = Modifier,
    draftSaveStatus: DraftSaveStatus = DraftSaveStatus.HIDDEN,
) {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current

    FunnelFocusScroll {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .clearFocusOnTap(focusManager),
        ) {
            CreateFunnelHeader(
                draftSaveStatus = draftSaveStatus,
                onClose = { onIntent(CreateAdditionalInfoIntent.LeaveFunnel) },
            )
            CreateStepIndicator(
                currentStep = 2,
                stepNameRes = R.string.create_step_additional_info,
            )
            when {
                // 복원 결과를 기다리는 동안은 본문을 비워 둔다 — 고른 스토리라인도 입력도 아직
                // 모르는 채로 그리면 재개 진입에서 빈 입력 화면이 번쩍인다.
                state.isRestoring -> Spacer(modifier = Modifier.weight(1f))

                state.isCompletingStory -> StoryCompletingContent(modifier = Modifier.weight(1f))

                else -> {
                    AdditionalInfoList(
                        modifier = Modifier.weight(1f),
                        storylineIndex = storylineIndex,
                        state = state,
                        onIntent = onIntent,
                    )
                    // IME가 열리면 CTA 영역을 콘텐츠에 돌려 입력 필드가 키보드 위로 스크롤되게 한다.
                    if (!imeVisible) {
                        CreateAdditionalInfoFooter(
                            storylineIndex = storylineIndex,
                            onIntent = onIntent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdditionalInfoList(
    storylineIndex: Int,
    state: CreateAdditionalInfoUiState,
    onIntent: (CreateAdditionalInfoIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val storyline = state.storylines.getOrNull(storylineIndex)
    val storylineText = storyline?.text.orEmpty()
    val recommendations = storyline?.recommendedInfos.orEmpty()
    val placeholders = stringArrayResource(R.array.create_additional_placeholders)
    val rowPadding =
        Modifier
            .padding(horizontal = ManyakTheme.spacing.gutter)
            .padding(bottom = ManyakTheme.spacing.compact)

    LazyColumn(modifier = modifier) {
        item { AdditionalInfoStepTitle() }
        item { SelectedStorylineBox(text = storylineText) }
        item {
            RecommendedInfoSection(
                recommendations = recommendations,
                selectedRecommendations = state.selectedRecommendations,
                onToggle = { text -> onIntent(CreateAdditionalInfoIntent.ToggleRecommendation(text)) },
            )
        }
        item { AdditionalInfoHeader(modifier = rowPadding) }
        itemsIndexed(state.additionalInfos, key = { _, input -> input.id }) { index, input ->
            AdditionalInfoRow(
                modifier = rowPadding,
                index = index,
                input = input,
                placeholder = placeholders[index % placeholders.size],
                onValueChange = { value ->
                    onIntent(CreateAdditionalInfoIntent.ChangeInput(input.id, value))
                },
                onRemove = { onIntent(CreateAdditionalInfoIntent.RemoveInput(input.id)) },
            )
        }
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                AddTrigger(
                    modifier = Modifier.align(Alignment.Center),
                    label = stringResource(R.string.create_add_info),
                    enabled = state.canAddInput,
                    onClick = { onIntent(CreateAdditionalInfoIntent.AddInput) },
                )
            }
        }
        state.completionFailure?.let { failure ->
            item { CompletionFailureNotice(failure = failure) }
        }
        item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom)) }
    }
}

/** 완성 실패 인라인 오류. 재시도는 "스토리 완성하기"를 다시 누르는 것이다. */
@Composable
private fun CompletionFailureNotice(
    failure: CompletionFailure,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter),
        text =
            stringResource(
                when (failure) {
                    CompletionFailure.CREDIT -> R.string.create_completion_error_credit
                    CompletionFailure.GENERAL -> R.string.create_completion_error
                },
            ),
        style = ManyakTheme.typography.bodyMedium,
        color = ManyakTheme.colors.textDanger,
    )
}

@Composable
private fun AdditionalInfoStepTitle(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.gutter, bottom = ManyakTheme.spacing.block),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.create_additional_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(R.string.create_additional_description),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

@Composable
private fun CreateAdditionalInfoFooter(
    storylineIndex: Int,
    onIntent: (CreateAdditionalInfoIntent) -> Unit,
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
            label = stringResource(R.string.create_cta_reselect_storyline),
            enabled = true,
            onClick = { onIntent(CreateAdditionalInfoIntent.ReselectStoryline) },
        )
        FunnelPrimaryButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.create_cta_complete_story),
            enabled = true,
            onClick = { onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex)) },
        )
    }
}

private fun previewAdditionalInfoState(): CreateAdditionalInfoUiState =
    CreateAdditionalInfoUiState(
        storylines =
            previewStorylines().map { storyline ->
                AdditionalInfoStoryline(
                    id = storyline.id,
                    text = storyline.storyline,
                    recommendedInfos = storyline.recommendedInfos.map { it.text },
                )
            },
    )

@Preview(showBackground = true, name = "추가 정보 · 라이트")
@Composable
private fun CreateAdditionalInfoScreenPreview() {
    ManyakTheme(darkTheme = false) {
        CreateAdditionalInfoContent(
            storylineIndex = 0,
            state = previewAdditionalInfoState(),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "추가 정보 · 추천 선택")
@Composable
private fun CreateAdditionalInfoScreenSelectedPreview() {
    ManyakTheme(darkTheme = false) {
        val base = previewAdditionalInfoState()
        CreateAdditionalInfoContent(
            storylineIndex = 0,
            state =
                base.copy(
                    selectedRecommendations =
                        base.storylines
                            .first()
                            .recommendedInfos
                            .take(1)
                            .toSet(),
                    additionalInfos =
                        listOf(
                            AdditionalInfoInput(id = 0, value = "배경은 현대의 서울로 해줘"),
                            AdditionalInfoInput(id = 1),
                        ),
                    nextInputId = 2,
                ),
            onIntent = {},
        )
    }
}
