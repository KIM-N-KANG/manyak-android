package app.manyak.feature.chat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

/** 툴바의 아이콘 버튼. 최소 터치 크기를 지키되 시각 크기는 아이콘에 맞춘다. */
@Composable
internal fun ComposerIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ManyakTheme.colors.textSubtle,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) tint else ManyakTheme.colors.textDisabled,
        )
    }
}

/** "상황 추가"·"대사 추가" 처럼 툴바에 놓이는 작은 채움 버튼. */
@Composable
internal fun ComposerChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(
                    if (enabled) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.backgroundDisabled,
                ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.component),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ManyakTheme.typography.labelLarge,
            color = if (enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled,
        )
    }
}

/**
 * 전송 버튼. 세 상태가 한 버튼을 나눠 쓰므로 **아이콘과 접근성 이름이 함께 바뀐다** — 아이콘만
 * 바뀌면 보조기술 사용자에게는 세 상태가 같은 버튼으로 읽힌다.
 */
@Composable
internal fun ComposerSendButton(
    state: SendButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            when (state.icon) {
                SendButtonIcon.SPINNER -> R.string.chat_composer_streaming
                SendButtonIcon.RANDOM -> R.string.chat_composer_send_random
                SendButtonIcon.SEND -> R.string.chat_composer_send
            },
        )
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.pill)
                .background(if (state.enabled) ManyakTheme.colors.brand else ManyakTheme.colors.backgroundDisabled)
                .clickable(
                    enabled = state.enabled,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = if (state.enabled) ManyakTheme.colors.textInverse else ManyakTheme.colors.textDisabled
        if (state.icon == SendButtonIcon.SPINNER) {
            ManyakProgressIndicator(
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                color = contentColor,
            )
        } else {
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.icon),
                painter =
                    painterResource(
                        if (state.icon == SendButtonIcon.RANDOM) R.drawable.ic_play_filled else R.drawable.ic_arrow_up,
                    ),
                contentDescription = label,
                tint = contentColor,
            )
        }
    }
}

/**
 * 컴포저의 입력창. 여러 줄을 허용하되 [maxLines] 를 넘으면 그 안에서 스크롤한다.
 *
 * `TextFieldValue` 를 받는 이유는 강조 마커 삽입이 **커서 위치를 되돌려 놓아야** 하기 때문이다.
 * 문자열만 주고받으면 마커를 넣은 뒤 커서가 끝으로 튄다.
 */
@Composable
internal fun ComposerTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textColor: Color = ManyakTheme.colors.text,
    leading: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        modifier =
            modifier
                .defaultMinSize(minHeight = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .border(BorderWidth, ManyakTheme.colors.borderInput, ManyakTheme.shapes.control),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        maxLines = maxLines,
        textStyle =
            ManyakTheme.typography.bodyMedium.copy(
                color = if (enabled) textColor else ManyakTheme.colors.textDisabled,
            ),
        cursorBrush = SolidColor(textColor),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = ManyakTheme.spacing.controlHorizontal,
                        vertical = ManyakTheme.spacing.controlVertical,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                if (leading != null) {
                    leading()
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = ManyakTheme.typography.bodyMedium,
                            color = ManyakTheme.colors.textSubtlest,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

/** 문자열만 들고 있는 상태를 커서 끝에 둔 편집 값으로 바꾼다. */
internal fun String.asTextFieldValue(): TextFieldValue = TextFieldValue(text = this, selection = TextRange(length))

private val BorderWidth = 1.dp
