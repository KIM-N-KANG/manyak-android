package app.manyak.create.general.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.manyak.create.general.entity.GeneralErrorReason
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.presentation.component.KeywordSectionLabel
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakMultilineTextField
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

@Suppress("LongParameterList")
internal class GeneralFormScope(
    val form: GeneralStoryForm,
    val onChange: (GeneralStoryForm) -> Unit,
    val errors: List<GeneralFieldError>,
    val serverErrors: Map<GeneralFieldTarget, String>,
    val enabled: Boolean,
    val onFieldBlur: (GeneralFieldTarget) -> Unit,
    val fieldPositions: MutableMap<GeneralFieldTarget, Float>,
    val uploadImage: (GeneralFieldTarget, String) -> Unit,
    val uploadingTargets: Set<GeneralFieldTarget>,
    val onPickerActiveChanged: (Boolean) -> Unit,
) {
    @Composable
    fun message(target: GeneralFieldTarget): String? =
        serverErrors[target] ?: errors.firstOrNull { it.target == target }?.let { generalErrorMessage(it) }

    fun anchor(target: GeneralFieldTarget): Modifier =
        Modifier.onGloballyPositioned {
            fieldPositions[target] = it.positionInRoot().y + it.size.height / 2f
        }
}

/**
 * 여러 줄 입력의 처음 높이와 더 자라지 않는 높이를 줄 수로 정한다. 긴 글을 받는 칸일수록 처음부터
 * 넉넉하게 두고, 상한을 넘으면 칸 안에서 스크롤한다.
 */
@Suppress("MagicNumber")
internal enum class GeneralTextHeight(
    val minLines: Int,
    val maxLines: Int,
) {
    SHORT(3, 7),
    LONG(5, 13),
    WORLD(7, 17),
    COMPACT(2, 7),
}

@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
internal fun GeneralFormScope.Input(
    target: GeneralFieldTarget,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = true,
    height: GeneralTextHeight? = null,
    placeholder: String = "",
    hint: String? = null,
    label: String = stringResource(target.labelRes()),
    showLabel: Boolean = true,
    showHint: Boolean = true,
    suffix: String? = null,
) {
    val message = message(target)
    var focused by remember(target) { mutableStateOf(false) }
    val inputModifier =
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                if (message != null) error(message)
            }.onFocusChanged {
                if (focused && !it.isFocused) onFieldBlur(target)
                focused = it.isFocused
            }
    val change: (String) -> Unit = { text ->
        val normalized =
            when {
                target.field == GeneralField.MIN_TURNS -> text
                height != null -> text.take(maxLength)
                else -> text.replace(Regex("[\\r\\n\\t]"), " ").take(maxLength)
            }
        onValueChange(normalized)
    }
    Column(
        modifier.then(anchor(target)),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        if (showLabel) KeywordSectionLabel(text = label, required = required)
        InputControl(target, value, maxLength, change, inputModifier, message != null, height, placeholder, suffix)
        if (showHint) GeneralFieldHint(message, hint)
    }
}

@Suppress("LongParameterList")
@Composable
private fun GeneralFormScope.InputControl(
    target: GeneralFieldTarget,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    isError: Boolean,
    height: GeneralTextHeight?,
    placeholder: String,
    suffix: String?,
) {
    if (height != null) {
        ManyakMultilineTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            enabled = enabled,
            isError = isError,
            modifier = modifier,
            minLines = height.minLines,
            maxLines = height.maxLines,
            footer = { ManyakInputCounter(value.length, maxLength) },
        )
    } else {
        ManyakTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            enabled = enabled,
            isError = isError,
            modifier = modifier,
            keyboardOptions = if (target.field == GeneralField.MIN_TURNS) NumberKeyboard else KeyboardOptions.Default,
            trailing = {
                // 단위가 붙는 칸은 글자 수 대신 단위를 둔다.
                if (suffix != null) {
                    Text(suffix, style = ManyakTheme.typography.bodySmall, color = ManyakTheme.colors.textSubtle)
                } else {
                    ManyakInputCounter(value.length, maxLength)
                }
            },
        )
    }
}

/** 칸 아래 설명. 오류가 있으면 같은 자리를 오류가 대신한다. */
@Composable
internal fun GeneralFieldHint(
    message: String?,
    hint: String? = null,
) {
    val text = message ?: hint
    if (text != null) {
        Text(
            text,
            style = ManyakTheme.typography.bodyMedium,
            color = if (message == null) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDanger,
        )
    }
}

@Composable
internal fun generalErrorMessage(error: GeneralFieldError): String {
    val label = stringResource(error.target.labelRes())
    val finalConsonant = hasFinalConsonant(label)
    return when (error.reason) {
        GeneralErrorReason.REQUIRED ->
            if (error.target.field == GeneralField.SUGGESTED_INPUT) {
                stringResource(CreateR.string.general_error_suggestions)
            } else {
                stringResource(
                    requiredMessageResource(finalConsonant),
                    label,
                )
            }
        GeneralErrorReason.TOO_SHORT ->
            stringResource(
                shortMessageResource(finalConsonant),
                label,
            )
        GeneralErrorReason.TOO_LONG -> stringResource(CreateR.string.general_error_long, label, error.limit ?: 0)
        GeneralErrorReason.DUPLICATE -> stringResource(CreateR.string.create_error_duplicate_name)
        GeneralErrorReason.GENDER_REQUIRED -> stringResource(CreateR.string.general_error_gender)
        GeneralErrorReason.GENRE_REQUIRED -> stringResource(CreateR.string.general_error_genres)
        GeneralErrorReason.INVALID_NUMBER -> stringResource(CreateR.string.general_error_turns)
        GeneralErrorReason.INVALID_GENRE -> stringResource(CreateR.string.general_error_genre_invalid)
        GeneralErrorReason.INVALID_COUNT -> stringResource(CreateR.string.general_error_count)
    }
}

internal fun GeneralTab.labelRes(): Int =
    when (this) {
        GeneralTab.PROFILE -> CreateR.string.general_tab_profile
        GeneralTab.SETTINGS -> CreateR.string.general_tab_settings
        GeneralTab.PROTAGONIST -> CreateR.string.general_tab_protagonist
        GeneralTab.SUPPORTING -> CreateR.string.general_tab_supporting
        GeneralTab.START -> CreateR.string.general_tab_start
        GeneralTab.EVENTS -> CreateR.string.general_tab_events
        GeneralTab.PUBLISH -> CreateR.string.general_tab_publish
    }

@Suppress("CyclomaticComplexMethod")
internal fun GeneralField.labelRes(): Int =
    when (this) {
        GeneralField.TITLE -> CreateR.string.general_title
        GeneralField.ONE_LINE_INTRO -> CreateR.string.general_intro
        GeneralField.WORLD -> CreateR.string.general_world
        GeneralField.PROGRESSION -> CreateR.string.general_progression
        GeneralField.NAME -> CreateR.string.general_name
        GeneralField.GENDER -> CreateR.string.general_gender
        GeneralField.FEATURE -> CreateR.string.general_feature
        GeneralField.CHARACTER_DESCRIPTION -> CreateR.string.general_character_description
        GeneralField.PROLOGUE -> CreateR.string.general_prologue
        GeneralField.SITUATION -> CreateR.string.general_situation
        GeneralField.SUGGESTED_INPUT -> CreateR.string.general_suggested_input
        GeneralField.MIN_TURNS -> CreateR.string.general_min_turns
        GeneralField.CONDITION -> CreateR.string.general_condition
        GeneralField.EPILOGUE -> CreateR.string.general_epilogue
        GeneralField.EVENT_DESCRIPTION -> CreateR.string.general_event_description
        GeneralField.KEY_SENTENCE -> CreateR.string.general_key_sentence
        GeneralField.GENRES -> CreateR.string.general_genres
        GeneralField.DESCRIPTION -> CreateR.string.general_description
        GeneralField.IMAGE -> CreateR.string.general_character_image
        GeneralField.COVER -> CreateR.string.general_cover
        GeneralField.ITEMS -> CreateR.string.general_items
    }

internal fun GeneralFieldTarget.labelRes(): Int =
    when {
        field != GeneralField.NAME -> field.labelRes()
        tab == GeneralTab.EVENTS -> CreateR.string.general_event_name
        tab == GeneralTab.START && itemId == startId -> CreateR.string.general_start_name
        tab == GeneralTab.START -> CreateR.string.general_ending_name
        else -> field.labelRes()
    }

private val NumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

private fun requiredMessageResource(finalConsonant: Boolean): Int =
    if (finalConsonant) CreateR.string.general_error_required_final else CreateR.string.general_error_required

private fun shortMessageResource(finalConsonant: Boolean): Int =
    if (finalConsonant) CreateR.string.general_error_short_final else CreateR.string.general_error_short

@Suppress("MagicNumber")
private fun hasFinalConsonant(label: String): Boolean =
    label.lastOrNull()?.let { it in '가'..'힣' && (it.code - '가'.code) % 28 != 0 } == true
