package app.manyak.create.keyword.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakInputCounter
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR

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
                text = stringResource(CreateR.string.create_add_keyword_dialog_title, categoryLabel),
                style = ManyakTheme.typography.titleMedium,
                color = ManyakTheme.colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
                Text(
                    text = stringResource(CreateR.string.create_add_keyword_dialog_description),
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
                Text(text = stringResource(CreateR.string.create_dialog_add), style = ManyakTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            ManyakTextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(CreateR.string.create_dialog_close),
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
            text = stringResource(CreateR.string.create_add_keyword_label),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.text,
        )
        ManyakTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = placeholder,
            isError = showEmptyError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailing = {
                ManyakInputCounter(
                    length = input.length,
                    maxLength = CreateKeywordUiState.CUSTOM_TAG_MAX_LENGTH,
                )
            },
        )
        if (showEmptyError) {
            Text(
                text = stringResource(CreateR.string.create_add_keyword_error_empty),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textDanger,
            )
        }
    }
}
