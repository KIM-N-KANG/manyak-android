package app.manyak.feature.create

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 진행 표시기가 노출하는 단계 수. 완료(생성 로딩)는 단계로 세지 않는다. */
private const val INDICATOR_STEP_COUNT = 3

/**
 * 간편 제작 퍼널의 키워드 선택 단계. 셸을 두르지 않는 전체 화면이라 헤더·인디케이터를 직접 그린다.
 *
 * 뒤로가기(헤더·시스템 모두)는 확인 없이 퍼널을 나간다 — 이탈 가드는 키워드 단계를 제외한
 * 단계에서만 활성이다.
 */
@Composable
fun CreateKeywordScreen(
    onLeaveFunnel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateKeywordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CreateKeywordContent(
        state = state,
        onBack = onLeaveFunnel,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun CreateKeywordContent(
    state: CreateKeywordUiState,
    onBack: () -> Unit,
    onIntent: (CreateKeywordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 다이얼로그 열림은 표현 상태라 화면이 들고, 확정된 입력만 Intent 로 올라간다.
    var addKeywordTarget by remember { mutableStateOf<KeywordTarget?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        CreateFunnelHeader(onBack = onBack)
        CreateStepIndicator(
            currentStep = 0,
            stepNameRes = R.string.create_step_keyword,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            KeywordStepTitle()
            CategoryTabs(state = state, onIntent = onIntent)
            CategoryDescription(state = state)
            CategoryContent(
                state = state,
                onIntent = onIntent,
                onOpenAddKeyword = { target -> addKeywordTarget = target },
            )
            Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom))
        }
        CreateKeywordFooter(state = state, onIntent = onIntent)
    }

    addKeywordTarget?.let { target ->
        AddKeywordDialog(
            categoryLabel = stringResource(target.category.labelRes),
            onDismiss = { addKeywordTarget = null },
            onSubmit = { name ->
                onIntent(CreateKeywordIntent.AddCustomTag(target, name))
                addKeywordTarget = null
            },
        )
    }
}

/**
 * 퍼널 공통 헤더. 뒤로가기 버튼과 화면 제목을 둔다.
 */
@Composable
private fun CreateFunnelHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.common_back),
                tint = ManyakTheme.colors.text,
            )
        }
        Text(
            modifier = Modifier.padding(start = ManyakTheme.spacing.inline),
            text = stringResource(R.string.create_title),
            style = ManyakTheme.typography.titleMedium,
            color = ManyakTheme.colors.text,
        )
    }
}

/**
 * 단계 인디케이터. 얇은 막대 3분절이며 완료·현재 단계는 주 동작 색으로 채운다.
 *
 * 단계 이름은 시각 라벨 없이 접근성 텍스트로만 제공한다(웹과 같은 계약). 막대들은 장식이므로
 * 시맨틱을 하나로 묶어 진행 상태 문장만 읽히게 한다.
 */
@Composable
private fun CreateStepIndicator(
    currentStep: Int,
    @StringRes stepNameRes: Int,
    modifier: Modifier = Modifier,
) {
    val description =
        stringResource(
            R.string.create_step_progress,
            stringResource(stepNameRes),
            currentStep + 1,
            INDICATOR_STEP_COUNT,
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter)
                .padding(top = ManyakTheme.spacing.compact, bottom = ManyakTheme.spacing.compact)
                .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        repeat(INDICATOR_STEP_COUNT) { index ->
            val reached = index <= currentStep
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(ManyakTheme.shapes.pill)
                        .background(
                            if (reached) ManyakTheme.colors.backgroundBrandBold else ManyakTheme.colors.border,
                        ),
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
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 하단 CTA. 필수 미충족 상태에서도 버튼은 활성이고, 누르면 이동하지 않고 버튼 위 푸터에 오류를
 * 표시한다. 오류가 있을 때만 푸터가 메시지 높이만큼 늘어난다.
 */
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
                state.validationErrorCategory == state.activeCategory -> R.string.create_error_select_keyword
                state.showDuplicateNameFooterError -> R.string.create_error_duplicate_name_footer
                else -> null
            }
        if (footerErrorRes != null) {
            Text(
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.compact),
                text = stringResource(footerErrorRes),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
        FooterButtons(
            isFirstCategory = isFirstCategory,
            isLastCategory = isLastCategory,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun FooterButtons(
    isFirstCategory: Boolean,
    isLastCategory: Boolean,
    onIntent: (CreateKeywordIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        if (!isFirstCategory) {
            Button(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = ManyakTheme.sizes.control),
                onClick = { onIntent(CreateKeywordIntent.GoPrevious) },
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.backgroundNeutral,
                        contentColor = ManyakTheme.colors.text,
                    ),
            ) {
                Text(text = stringResource(R.string.create_cta_previous), style = ManyakTheme.typography.labelLarge)
            }
        }
        Button(
            modifier =
                Modifier
                    .weight(if (isFirstCategory) 1f else 2f)
                    .heightIn(min = ManyakTheme.sizes.control),
            onClick = {
                val intent =
                    if (isLastCategory) {
                        CreateKeywordIntent.GenerateStorylines
                    } else {
                        CreateKeywordIntent.GoNext
                    }
                onIntent(intent)
            },
            shape = ManyakTheme.shapes.control,
            // 웹 primary 와 같은 브랜드 원색을 쓰는 퍼널 주 CTA 예외 — 근거는 DESIGN.md 퍼널 절.
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            val labelRes =
                if (isLastCategory) R.string.create_cta_generate_storylines else R.string.create_cta_next
            Text(text = stringResource(labelRes), style = ManyakTheme.typography.labelLarge)
        }
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
