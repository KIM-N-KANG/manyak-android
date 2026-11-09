package app.manyak.my.persona.presentation

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.designsystem.component.FocusScrollMargin
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakMultilineTextField
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakSelectField
import app.manyak.designsystem.component.ManyakSelectOption
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.persona.entity.PersonaGender
import app.manyak.my.presentation.component.MyDetailHeader
import app.manyak.my.presentation.component.MyFieldLabel
import app.manyak.my.presentation.component.MyFieldMessage
import app.manyak.my.presentation.component.MyPrimaryButton
import app.manyak.common.R as CommonR
import app.manyak.my.R as MyR

/**
 * 페르소나 생성과 수정. [personaId] 가 있으면 수정이고, [originStoryId] 는 스토리 상세에서 왔을 때 그 스토리다.
 * 저장하면 알리고 [onDone] 으로 들어온 화면에 돌아간다.
 */
@Composable
fun PersonaFormScreen(
    personaId: String?,
    originStoryId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonaFormViewModel =
        hiltViewModel<PersonaFormViewModel, PersonaFormViewModel.Factory>(
            creationCallback = { factory -> factory.create(personaId, originStoryId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PersonaFormEffects(viewModel = viewModel, onDone = onDone)

    FocusScrollMargin {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            MyDetailHeader(
                titleRes = if (state.isEdit) MyR.string.persona_edit_header else MyR.string.persona_create_header,
                onBack = onBack,
            )
            when (state.load) {
                PersonaFormLoad.LOADING ->
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ManyakProgressIndicator()
                    }

                PersonaFormLoad.FAILED ->
                    LoadFailedContent(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(ManyakTheme.spacing.gutter),
                        message = stringResource(MyR.string.persona_list_load_failed),
                        onRetry = { viewModel.onIntent(PersonaFormIntent.Retry) },
                    )

                PersonaFormLoad.NOT_FOUND ->
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(MyR.string.persona_not_found),
                            style = ManyakTheme.typography.bodyMedium,
                            color = ManyakTheme.colors.textSubtle,
                        )
                    }

                PersonaFormLoad.READY ->
                    PersonaFormContent(
                        state = state,
                        onIntent = viewModel::onIntent,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }
}

/** 저장 결과를 토스트로 알리고, 저장했으면 들어온 화면으로 돌아간다. */
@Composable
private fun PersonaFormEffects(
    viewModel: PersonaFormViewModel,
    onDone: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                val message =
                    when (effect) {
                        is PersonaFormEffect.Saved ->
                            if (effect.isEdit) MyR.string.persona_updated else MyR.string.persona_created
                        PersonaFormEffect.ShowLimitReached -> CommonR.string.persona_limit_reached
                        is PersonaFormEffect.ShowSaveFailed ->
                            if (effect.isEdit) MyR.string.persona_update_failed else MyR.string.persona_create_failed
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                if (effect is PersonaFormEffect.Saved) currentOnDone()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaFormContent(
    state: PersonaFormUiState,
    onIntent: (PersonaFormIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 루트가 safeDrawing 으로 IME 여백을 이미 먹고 소비하므로 여기서 다시 끼지 않는다.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = ManyakTheme.spacing.gutter),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        ) {
            PersonaFormHeadline(isEdit = state.isEdit)
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section)) {
                PersonaBasicInfo(state = state, onIntent = onIntent)
                PersonaFeature(state = state, onIntent = onIntent)
            }
        }
        // IME 가 열리면 버튼 자리를 콘텐츠에 돌려 입력 칸이 키보드 위로 스크롤되게 한다.
        if (!WindowInsets.isImeVisible) {
            val submittingLabel =
                stringResource(
                    if (state.isEdit) MyR.string.persona_edit_submitting else MyR.string.persona_create_submitting,
                )
            MyPrimaryButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = ManyakTheme.spacing.gutter)
                        .semantics { if (state.isSubmitting) contentDescription = submittingLabel },
                label =
                    stringResource(
                        if (state.isEdit) MyR.string.persona_edit_submit else MyR.string.persona_create_submit,
                    ),
                isLoading = state.isSubmitting,
                onClick = { onIntent(PersonaFormIntent.Submit) },
            )
        }
    }
}

@Composable
private fun PersonaFormHeadline(
    isEdit: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(
            text = stringResource(MyR.string.persona_create_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        Text(
            text =
                stringResource(
                    if (isEdit) MyR.string.persona_edit_description else MyR.string.persona_create_description,
                ),
            style = ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 이름과 성별을 한 줄에 두고, 둘 다 틀렸으면 이름 오류를 먼저 보인다. */
@Composable
private fun PersonaBasicInfo(
    state: PersonaFormUiState,
    onIntent: (PersonaFormIntent) -> Unit,
) {
    val nameError = stringResource(MyR.string.persona_error_name).takeIf { state.nameError }
    val genderError = stringResource(MyR.string.persona_error_gender).takeIf { state.genderError }
    val nameLabel = stringResource(MyR.string.persona_name)
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        MyFieldLabel(text = stringResource(MyR.string.persona_basic_info), isRequired = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            ManyakTextField(
                modifier =
                    Modifier.weight(NAME_WEIGHT).semantics {
                        contentDescription = nameLabel
                        if (nameError != null) error(nameError)
                    },
                value = state.name,
                onValueChange = { onIntent(PersonaFormIntent.NameChanged(it)) },
                placeholder = stringResource(MyR.string.persona_name_placeholder),
                enabled = !state.isSubmitting,
                isError = nameError != null,
                trailing = { ManyakInputCounter(state.name.length, PersonaFormUiState.NAME_MAX_LENGTH) },
            )
            PersonaGenderField(
                gender = state.gender,
                error = genderError,
                enabled = !state.isSubmitting,
                onSelect = { onIntent(PersonaFormIntent.GenderChanged(it)) },
                modifier = Modifier.weight(GENDER_WEIGHT),
            )
        }
        val message = nameError ?: genderError
        MyFieldMessage(
            text = message ?: stringResource(MyR.string.persona_basic_info_hint),
            isError = message != null,
        )
    }
}

@Composable
private fun PersonaGenderField(
    gender: PersonaGender?,
    error: String?,
    enabled: Boolean,
    onSelect: (PersonaGender) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(MyR.string.persona_gender)
    val options =
        listOf(
            ManyakSelectOption<PersonaGender?>(PersonaGender.MALE, stringResource(MyR.string.persona_gender_male)),
            ManyakSelectOption<PersonaGender?>(PersonaGender.FEMALE, stringResource(MyR.string.persona_gender_female)),
        )
    val fieldModifier =
        modifier
            .then(
                if (error != null) {
                    Modifier.border(
                        ManyakTheme.sizes.inputBorderWidth,
                        ManyakTheme.colors.borderDanger,
                        ManyakTheme.shapes.control,
                    )
                } else {
                    Modifier
                },
            ).semantics {
                contentDescription = label
                if (error != null) error(error)
            }
    // 성별은 필수라 고르지 않은 상태는 메뉴 항목으로 두지 않고 앵커의 흐린 문구로만 보인다.
    if (enabled) {
        ManyakSelectField(options, gender, { it?.let(onSelect) }, fieldModifier, placeholder = label)
    } else {
        ManyakTextField(
            value = options.firstOrNull { it.value == gender }?.label.orEmpty(),
            onValueChange = {},
            placeholder = label,
            modifier = fieldModifier,
            enabled = false,
        )
    }
}

@Composable
private fun PersonaFeature(
    state: PersonaFormUiState,
    onIntent: (PersonaFormIntent) -> Unit,
) {
    val label = stringResource(MyR.string.persona_feature)
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        MyFieldLabel(text = label, isRequired = true)
        ManyakMultilineTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.feature,
            onValueChange = { onIntent(PersonaFormIntent.FeatureChanged(it)) },
            placeholder = stringResource(MyR.string.persona_feature_placeholder),
            enabled = !state.isSubmitting,
            isError = state.featureError,
            contentDescription = label,
            minLines = FEATURE_MIN_LINES,
            maxLines = FEATURE_MAX_LINES,
            footer = { ManyakInputCounter(state.feature.length, PersonaFormUiState.FEATURE_MAX_LENGTH) },
        )
        MyFieldMessage(
            text =
                stringResource(
                    if (state.featureError) MyR.string.persona_error_feature else MyR.string.persona_feature_hint,
                ),
            isError = state.featureError,
        )
    }
}

/** 일반 제작 주인공 칸과 같은 비율이다. */
private const val NAME_WEIGHT = 3f
private const val GENDER_WEIGHT = 2f
private const val FEATURE_MIN_LINES = 5
private const val FEATURE_MAX_LINES = 13

@Preview(showBackground = true, name = "페르소나 생성 오류")
@Composable
private fun PersonaFormPreview() {
    ManyakTheme(darkTheme = false) {
        PersonaFormContent(
            state =
                PersonaFormUiState(
                    isEdit = false,
                    load = PersonaFormLoad.READY,
                    nameError = true,
                    genderError = true,
                    featureError = true,
                ),
            onIntent = {},
        )
    }
}
