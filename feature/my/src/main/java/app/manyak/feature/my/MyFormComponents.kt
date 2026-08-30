package app.manyak.feature.my

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 마이 하위 화면들의 폼 조각. 초대·피드백 두 화면이 같은 버튼과 입력창을 쓴다.
 *
 * `:core:ui` 로 올리지 않는다 — 아직 이 모듈 밖에 쓰는 곳이 없다.
 */
@Composable
@Suppress("LongParameterList")
internal fun MyPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    @DrawableRes iconRes: Int? = null,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
            ButtonContent(label = label, iconRes = iconRes, modifier = Modifier.alpha(if (isLoading) 0f else 1f))
            if (isLoading) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = LocalContentColor.current,
                )
            }
        }
    }
}

/** 주 동작 옆에 나란히 서는 보조 버튼. 채움 없이 경계만 두어 무게를 낮춘다. */
@Composable
internal fun MyOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = enabled,
        shape = ManyakTheme.shapes.control,
        border = BorderStroke(1.dp, ManyakTheme.colors.border),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.surface,
                contentColor = ManyakTheme.colors.text,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        ButtonContent(label = label, iconRes = iconRes)
    }
}

@Composable
private fun ButtonContent(
    label: String,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            )
        }
        Text(text = label, style = ManyakTheme.typography.labelLarge, maxLines = 1)
    }
}

/** 입력 항목의 라벨. [isRequired] 는 웹과 같이 별표 하나로만 드러낸다. */
@Composable
internal fun MyFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
) {
    Row(modifier = modifier) {
        Text(text = text, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
        if (isRequired) {
            Text(
                text = stringResource(R.string.form_required_mark),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textDanger,
            )
        }
    }
}

/**
 * 입력창. 여러 줄 입력은 [minHeight] 를 올리고 [counter] 로 글자 수를 아래에 붙인다.
 */
@Composable
@Suppress("LongParameterList")
internal fun MyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = ManyakTheme.sizes.input,
    textStyle: TextStyle = ManyakTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    counter: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor =
        when {
            isError -> ManyakTheme.colors.borderDanger
            focused -> ManyakTheme.colors.borderInput
            else -> ManyakTheme.colors.border
        }
    BasicTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = textStyle.copy(color = ManyakTheme.colors.text),
        cursorBrush = SolidColor(ManyakTheme.colors.text),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                        .border(1.dp, borderColor, ManyakTheme.shapes.control)
                        .padding(
                            horizontal = ManyakTheme.spacing.controlHorizontal,
                            vertical = ManyakTheme.spacing.controlVertical,
                        ),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                // 글자 수는 입력창 바닥에 붙어야 한다. 본문이 짧아도 위로 딸려 올라오지 않게 남은 높이를 준다.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (counter == null) Modifier else Modifier.weight(1f)),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = ManyakTheme.colors.textDisabled,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        )
                    }
                    innerTextField()
                }
                counter?.let { Box(modifier = Modifier.align(Alignment.End)) { it() } }
            }
        },
    )
}

/** 입력 아래에 붙는 오류·보조 문구. */
@Composable
internal fun MyFieldMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Text(
        modifier = modifier,
        text = text,
        style = ManyakTheme.typography.bodySmall,
        color = if (isError) ManyakTheme.colors.textDanger else ManyakTheme.colors.textSubtle,
    )
}
