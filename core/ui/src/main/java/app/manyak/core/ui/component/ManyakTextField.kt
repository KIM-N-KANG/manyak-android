package app.manyak.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 한 줄 입력창.
 *
 * 초점·오류를 색 하나가 아니라 경계선으로 말한다. 값 옆에 세울 것(글자 수 등)은 [trailing] 이 맡는다.
 */
@Composable
@Suppress("LongParameterList")
fun ManyakTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = ManyakTheme.typography.bodyMedium.copy(color = ManyakTheme.colors.text),
        cursorBrush = SolidColor(ManyakTheme.colors.text),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ManyakTheme.sizes.input)
                        .fieldSurface(isError = isError, isFocused = focused),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FieldPlaceholder(value = value, placeholder = placeholder, maxLines = 1)
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * 여러 줄 입력창. 높이를 고정하지 않고 내용만큼 자라며 [footer] 는 오른쪽 아래에 붙는다.
 *
 * @param contentDescription 라벨이 입력창과 떨어져 있을 때 화면 낭독기에 읽힐 이름.
 */
@Composable
@Suppress("LongParameterList")
fun ManyakMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    contentDescription: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    footer: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        modifier =
            modifier.then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = ManyakTheme.typography.bodyMedium.copy(color = ManyakTheme.colors.text),
        cursorBrush = SolidColor(ManyakTheme.colors.text),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Column(
                modifier = Modifier.fieldSurface(isError = isError, isFocused = focused),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FieldPlaceholder(value = value, placeholder = placeholder, maxLines = Int.MAX_VALUE)
                    innerTextField()
                }
                footer?.let { Box(modifier = Modifier.align(Alignment.End)) { it() } }
            }
        },
    )
}

/** 입력창에 붙는 글자 수. 입력 상한이 있는 자리에서만 쓴다. */
@Composable
fun ManyakInputCounter(
    length: Int,
    maxLength: Int,
) {
    Text(
        text = stringResource(R.string.input_counter, length, maxLength),
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textSubtle,
    )
}

/** 입력창의 채움·경계·여백. 한 줄과 여러 줄이 같은 표면을 쓴다. */
@Composable
private fun Modifier.fieldSurface(
    isError: Boolean,
    isFocused: Boolean,
): Modifier {
    val borderColor =
        when {
            isError -> ManyakTheme.colors.borderDanger
            isFocused -> ManyakTheme.colors.borderInput
            else -> ManyakTheme.colors.border
        }
    return this
        .clip(ManyakTheme.shapes.control)
        .background(ManyakTheme.colors.surfaceRaised)
        .border(1.dp, borderColor, ManyakTheme.shapes.control)
        .padding(
            horizontal = ManyakTheme.spacing.controlHorizontal,
            vertical = ManyakTheme.spacing.controlVertical,
        )
}

@Composable
private fun FieldPlaceholder(
    value: String,
    placeholder: String,
    maxLines: Int,
) {
    if (value.isNotEmpty()) return
    Text(
        text = placeholder,
        style = ManyakTheme.typography.bodyMedium,
        color = ManyakTheme.colors.textDisabled,
        maxLines = maxLines,
    )
}
