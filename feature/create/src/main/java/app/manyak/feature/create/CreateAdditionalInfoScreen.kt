package app.manyak.feature.create

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 추가 정보 단계. 뒤로가기와 "다시 선택하기" 모두 스토리라인 선택 단계 복귀다. */
@Composable
fun CreateAdditionalInfoScreen(
    storylineIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateAdditionalInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CreateAdditionalInfoContent(
        storylineIndex = storylineIndex,
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateAdditionalInfoContent(
    storylineIndex: Int,
    state: CreateAdditionalInfoUiState,
    onBack: () -> Unit,
    onIntent: (CreateAdditionalInfoIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clearFocusOnTap(focusManager),
    ) {
        CreateFunnelHeader(onBack = onBack)
        CreateStepIndicator(
            currentStep = 2,
            stepNameRes = R.string.create_step_additional_info,
        )
        AdditionalInfoList(
            modifier = Modifier.weight(1f),
            storylineIndex = storylineIndex,
            state = state,
            onIntent = onIntent,
        )
        // IME가 열리면 CTA 영역을 콘텐츠에 돌려 입력 필드가 키보드 위로 스크롤되게 한다.
        if (!imeVisible) {
            CreateAdditionalInfoFooter(onBack = onBack, onIntent = onIntent)
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
    // 생성 API 연동 전까지 본문·추천 정보를 임시 데이터에서 얻는다.
    val storylineText =
        CreateStorylineViewModel.PLACEHOLDER_STORYLINES.getOrNull(storylineIndex).orEmpty()
    val recommendations =
        CreateAdditionalInfoViewModel.PLACEHOLDER_RECOMMENDED_INFOS.getOrNull(storylineIndex).orEmpty()
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
        item { Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom)) }
    }
}

@Composable
private fun AdditionalInfoStepTitle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(ManyakTheme.spacing.gutter),
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
    onBack: () -> Unit,
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
            onClick = onBack,
        )
        FunnelPrimaryButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.create_cta_complete_story),
            enabled = true,
            onClick = { onIntent(CreateAdditionalInfoIntent.CompleteStory) },
        )
    }
}

@Preview(showBackground = true, name = "추가 정보 · 라이트")
@Composable
private fun CreateAdditionalInfoScreenPreview() {
    ManyakTheme(darkTheme = false) {
        CreateAdditionalInfoContent(
            storylineIndex = 0,
            state = CreateAdditionalInfoUiState(),
            onBack = {},
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "추가 정보 · 추천 선택")
@Composable
private fun CreateAdditionalInfoScreenSelectedPreview() {
    ManyakTheme(darkTheme = false) {
        CreateAdditionalInfoContent(
            storylineIndex = 0,
            state =
                CreateAdditionalInfoUiState(
                    selectedRecommendations =
                        setOf(CreateAdditionalInfoViewModel.PLACEHOLDER_RECOMMENDED_INFOS[0][0]),
                    additionalInfos =
                        listOf(
                            AdditionalInfoInput(id = 0, value = "배경은 현대의 서울로 해줘"),
                            AdditionalInfoInput(id = 1),
                        ),
                    nextInputId = 2,
                ),
            onBack = {},
            onIntent = {},
        )
    }
}
