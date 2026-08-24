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
import androidx.compose.material3.ButtonDefaults
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
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

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
                append(" ")
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

@Composable
internal fun KeywordChip(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) ManyakTheme.colors.backgroundBrandSubtle else ManyakTheme.colors.surfaceRaised
    val borderColor = if (selected) ManyakTheme.colors.borderBrand else ManyakTheme.colors.border
    val textColor =
        when {
            selected -> ManyakTheme.colors.textBrand
            enabled -> ManyakTheme.colors.text
            else -> ManyakTheme.colors.textDisabled
        }
    Box(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(background)
                .border(1.dp, borderColor, ManyakTheme.shapes.control)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ).padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name, style = ManyakTheme.typography.bodyMedium, color = textColor, maxLines = 1)
    }
}

@Composable
internal fun AddTrigger(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled
    Row(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.backgroundNeutral)
                .border(1.dp, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = label,
            style = ManyakTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

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
                        .heightIn(min = ManyakTheme.sizes.input)
                        .clip(ManyakTheme.shapes.control)
                        .background(ManyakTheme.colors.backgroundNeutral),
            )
        }
    }
}

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
            focused -> ManyakTheme.colors.borderInput
            else -> ManyakTheme.colors.border
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.surfaceRaised)
                .border(1.dp, borderColor, ManyakTheme.shapes.control)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textDisabled,
                    maxLines = 1,
                )
            }
            BasicTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                textStyle = ManyakTheme.typography.bodyMedium.copy(color = ManyakTheme.colors.text),
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

@Composable
internal fun AddKeywordDialog(
    categoryLabel: String,
    placeholder: String,
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
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                Text(
                    text = stringResource(R.string.create_add_keyword_dialog_description),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
                AddKeywordDialogBody(
                    input = input,
                    placeholder = placeholder,
                    showEmptyError = showEmptyError,
                    onInputChange = { newValue ->
                        input = newValue.take(CreateKeywordUiState.CUSTOM_TAG_MAX_LENGTH)
                        if (newValue.isNotBlank()) showEmptyError = false
                    },
                    onDone = submit,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = submit,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
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
    placeholder: String,
    showEmptyError: Boolean,
    onInputChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
        Text(
            modifier = Modifier.padding(top = ManyakTheme.spacing.compact),
            text = stringResource(R.string.create_add_keyword_label),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.text,
        )
        KeywordTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = placeholder,
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
