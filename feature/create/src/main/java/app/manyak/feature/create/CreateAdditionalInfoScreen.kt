package app.manyak.feature.create

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ScrollEdgeFade
import app.manyak.core.ui.theme.ManyakTheme
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

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
    val draftSave by viewModel.draftSave.collectAsStateWithLifecycle()
    val currentOnEnterChat by rememberUpdatedState(onEnterChat)
    val currentOnLeaveFunnel by rememberUpdatedState(onLeaveFunnel)
    val currentOnBackToStoryline by rememberUpdatedState(onBackToStoryline)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // 디바이스 뒤로가기도 앱 바 닫기와 같은 이탈 처리를 거친다.
    BackHandler { viewModel.onIntent(CreateAdditionalInfoIntent.LeaveFunnel) }

    SaveDraftWhenBackgrounded { viewModel.onIntent(CreateAdditionalInfoIntent.SaveDraft) }

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

                    is CreateAdditionalInfoEffect.ShowCompletionFailure ->
                        Toast
                            .makeText(context, effect.failure.messageRes(), Toast.LENGTH_SHORT)
                            .show()

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
        draftSave = draftSave,
    )

    state.exitWarning?.let { warning ->
        FunnelExitWarningDialog(
            warning = warning,
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
    draftSave: DraftSaveUiState = DraftSaveUiState(),
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
                draftSave = draftSave,
                onSaveDraft = { onIntent(CreateAdditionalInfoIntent.SaveDraft) },
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
                    // 스크롤 본문이 푸터 경계에서 딱 잘리므로 바닥에 페이드를 겹친다.
                    Box(modifier = Modifier.weight(1f)) {
                        AdditionalInfoList(
                            modifier = Modifier.fillMaxSize(),
                            storylineIndex = storylineIndex,
                            state = state,
                            onIntent = onIntent,
                        )
                        ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
                    }
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
    val horizontalPadding = Modifier.padding(horizontal = ManyakTheme.spacing.gutter)
    val listState = rememberLazyListState()
    FollowNewInput(count = state.additionalInfos.size, listState = listState)

    LazyColumn(modifier = modifier, state = listState) {
        item { AdditionalInfoStepTitle() }
        item { SelectedStorylineBox(text = storylineText) }
        item {
            RecommendedInfoSection(
                recommendations = recommendations,
                selectedRecommendations = state.selectedRecommendations,
                onToggle = { text -> onIntent(CreateAdditionalInfoIntent.ToggleRecommendation(text)) },
            )
        }
        item {
            AdditionalInfoHeader(
                modifier = horizontalPadding.padding(bottom = ManyakTheme.spacing.compact),
            )
        }
        item {
            AdditionalInfoRows(
                modifier = horizontalPadding,
                inputs = state.additionalInfos,
                onValueChange = { id, value ->
                    onIntent(CreateAdditionalInfoIntent.ChangeInput(id, value))
                },
                onRemove = { id -> onIntent(CreateAdditionalInfoIntent.RemoveInput(id)) },
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
        item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.gutter)) }
    }
}

/**
 * 칸이 늘어나면 목록 끝을 따라간다. 새 칸은 목록 아래쪽에 생겨, 그대로 두면 "정보 추가" 버튼과 함께
 * 푸터 밖으로 밀려 방금 무엇이 늘었는지 보이지 않는다.
 *
 * **자라는 프레임마다 곧바로 끝에 붙인다** — 스크롤을 따로 애니메이션하면 등장 애니메이션이 끝난
 * 뒤에야 움직이기 시작해 누르고 한 박자 뒤에 반응하는 것처럼 보인다. 움직임은 칸이 자라는
 * 애니메이션이 이미 만들고 있다.
 *
 * 등장이 끝날 때까지만 따라간다 — 그 뒤로도 붙잡으면 사용자가 위로 올린 스크롤을 되돌리게 된다.
 * 구성 변경에서 다시 붙잡지 않도록 직전 개수는 saveable 로 든다.
 */
@Composable
private fun FollowNewInput(
    count: Int,
    listState: LazyListState,
) {
    var lastCount by rememberSaveable { mutableIntStateOf(count) }
    val millis = ManyakTheme.motion.elementEnterMillis
    LaunchedEffect(count) {
        val grew = count > lastCount
        lastCount = count
        if (!grew) return@LaunchedEffect
        withTimeoutOrNull(millis * FOLLOW_TIMEOUT_FACTOR) {
            while (isActive) {
                withFrameNanos { }
                // 마지막 항목을 향해 보내면 목록 끝에서 멈춰 바닥에 붙는다. 아직 측정 전이라 항목
                // 수를 모르는 프레임은 건너뛴다 — 0 을 그대로 쓰면 목록이 맨 위로 튄다.
                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                if (lastIndex >= 0) listState.scrollToItem(lastIndex)
            }
        }
    }
}

/** 완성 실패 토스트 문구. 재시도는 "스토리 완성하기"를 다시 누르는 것이다. */
@StringRes
private fun CompletionFailure.messageRes(): Int =
    when (this) {
        CompletionFailure.CREDIT -> R.string.create_completion_error_credit
        CompletionFailure.GENERAL -> R.string.create_completion_error
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // 이 푸터에는 오류 문구가 없어 위를 띄우지 않는다 — 콘텐츠와의 경계는 페이드가 맡는다.
                .padding(bottom = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        StoryCompletionCostRow()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ManyakTheme.spacing.gutter),
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
}

/** 완성 비용 안내. 좌우 여백 없이 화면 폭을 채워 CTA 행과 다른 층으로 읽힌다. */
@Composable
private fun StoryCompletionCostRow(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ManyakTheme.sizes.input)
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.create_completion_credit_cost_label),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        Text(
            text = stringResource(R.string.create_completion_credit_cost_amount),
            style = ManyakTheme.typography.bodyMediumStrong,
            color = ManyakTheme.colors.text,
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

private const val FOLLOW_TIMEOUT_FACTOR = 2L
