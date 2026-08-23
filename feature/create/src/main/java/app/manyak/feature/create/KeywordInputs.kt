package app.manyak.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 칩 목록 위의 섹션 라벨. 필수 항목에는 `*` 를 붙인다. */
@Composable
internal fun KeywordSectionLabel(
    text: String,
    required: Boolean,
    modifier: Modifier = Modifier,
) {
    val label =
        buildAnnotatedString {
            append(text)
            if (required) {
                withStyle(SpanStyle(color = ManyakTheme.colors.textDanger)) { append("*") }
            }
        }
    Text(
        modifier = modifier,
        text = label,
        style = ManyakTheme.typography.labelLarge,
        color = ManyakTheme.colors.text,
    )
}

/**
 * 선택 가능한 키워드 칩. 선택은 색과 테두리 둘로 말한다 — 색 하나로만 구분하지 않는다.
 * 상한에 도달하면 미선택 칩을 비활성화한다.
 */
@Composable
internal fun KeywordChip(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) ManyakTheme.colors.backgroundBrandSubtle else ManyakTheme.colors.backgroundNeutral
    val textColor =
        when {
            selected -> ManyakTheme.colors.textBrand
            enabled -> ManyakTheme.colors.text
            else -> ManyakTheme.colors.textDisabled
        }
    Box(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.pill)
                .background(background)
                .then(
                    if (selected) {
                        Modifier.border(1.dp, ManyakTheme.colors.borderBrand, ManyakTheme.shapes.pill)
                    } else {
                        Modifier
                    },
                ).clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.component, vertical = ManyakTheme.spacing.compact),
    ) {
        Text(text = name, style = ManyakTheme.typography.bodyMedium, color = textColor, maxLines = 1)
    }
}

/** "키워드 추가" 트리거. 상한에 도달하면 비활성화한다. */
@Composable
internal fun AddKeywordTrigger(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled
    Row(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.pill)
                .background(ManyakTheme.colors.backgroundNeutral)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.component, vertical = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = stringResource(R.string.create_add_keyword),
            style = ManyakTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 태그 로딩 중의 스켈레톤 칩. 실제 칩과 같은 높이·모양으로 자리를 잡는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeywordChipSkeleton(modifier: Modifier = Modifier) {
    val widths = listOf(64, 80, 56, 72, 64, 88, 56, 72)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        widths.forEach { width ->
            Box(
                modifier =
                    Modifier
                        .width(width.dp)
                        .heightIn(min = 36.dp)
                        .clip(ManyakTheme.shapes.pill)
                        .background(ManyakTheme.colors.backgroundNeutral),
            )
        }
    }
}

/**
 * 단일 행 입력 필드. 디자인 시스템의 text-field 조합(중립 배경 + 경계 + 포커스/오류 경계색)을 따른다.
 * [trailing] 은 글자 수 카운터처럼 입력 오른쪽에 붙는 내용이다.
 */
@Composable
internal fun KeywordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor =
        when {
            isError -> ManyakTheme.colors.borderDanger
            focused -> ManyakTheme.colors.borderFocused
            else -> ManyakTheme.colors.borderInput
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.backgroundNeutral)
                .border(1.dp, borderColor, ManyakTheme.shapes.control)
                .padding(horizontal = ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = ManyakTheme.typography.bodyLarge,
                    color = ManyakTheme.colors.textDisabled,
                    maxLines = 1,
                )
            }
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                textStyle = ManyakTheme.typography.bodyLarge.copy(color = ManyakTheme.colors.text),
                cursorBrush = SolidColor(ManyakTheme.colors.text),
                singleLine = true,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
            )
        }
        trailing?.invoke()
    }
}

/** 입력 오른쪽의 글자 수 카운터. */
@Composable
internal fun InputCounter(
    length: Int,
    maxLength: Int,
) {
    Text(
        text = stringResource(R.string.create_input_counter, length, maxLength),
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textSubtle,
    )
}

/**
 * 커스텀 키워드 추가 다이얼로그. "추가하기"는 빈 입력에서도 활성이고, 빈 값으로 누르면 입력창 아래에
 * 오류를 표시한다. 유효한 값을 입력하거나 다이얼로그를 닫으면 오류가 사라진다.
 */
@Composable
internal fun AddKeywordDialog(
    categoryLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showEmptyError by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (input.isBlank()) showEmptyError = true else onSubmit(input.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.overlay,
        title = {
            Text(
                text = stringResource(R.string.create_add_keyword_dialog_title, categoryLabel),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            AddKeywordDialogBody(
                input = input,
                showEmptyError = showEmptyError,
                onInputChange = { newValue ->
                    input = newValue.take(CreateKeywordUiState.CUSTOM_TAG_MAX_LENGTH)
                    if (newValue.isNotBlank()) showEmptyError = false
                },
                onDone = submit,
            )
        },
        confirmButton = {
            Button(
                onClick = submit,
                shape = ManyakTheme.shapes.control,
            ) {
                Text(text = stringResource(R.string.create_dialog_add), style = ManyakTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.create_dialog_close),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
        },
    )
}

@Composable
private fun AddKeywordDialogBody(
    input: String,
    showEmptyError: Boolean,
    onInputChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(
            text = stringResource(R.string.create_add_keyword_dialog_description),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        KeywordTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = "",
            isError = showEmptyError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailing = {
                InputCounter(
                    length = input.length,
                    maxLength = CreateKeywordUiState.CUSTOM_TAG_MAX_LENGTH,
                )
            },
        )
        if (showEmptyError) {
            Text(
                text = stringResource(R.string.create_add_keyword_error_empty),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
    }
}

/**
 * 성별 셀렉트. 기본값(랜덤)은 고르지 않은 상태와 같아 null 로 남는다.
 */
@Composable
internal fun GenderSelectField(
    gender: CharacterGender?,
    onGenderChange: (CharacterGender?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            null to stringResource(R.string.create_gender_random),
            CharacterGender.MALE to stringResource(R.string.create_gender_male),
            CharacterGender.FEMALE to stringResource(R.string.create_gender_female),
        )
    val selectedLabel = options.first { it.first == gender }.second

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ManyakTheme.sizes.control)
                    .clip(ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.backgroundNeutral)
                    .border(1.dp, ManyakTheme.colors.borderInput, ManyakTheme.shapes.control)
                    .clickable { expanded = true }
                    .padding(horizontal = ManyakTheme.spacing.component),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedLabel,
                style = ManyakTheme.typography.bodyLarge,
                // 랜덤은 고르지 않은 상태라 placeholder 처럼 낮춰 보여 준다.
                color = if (gender == null) ManyakTheme.colors.textSubtle else ManyakTheme.colors.text,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(text = label, style = ManyakTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        onGenderChange(value)
                    },
                )
            }
        }
    }
}
