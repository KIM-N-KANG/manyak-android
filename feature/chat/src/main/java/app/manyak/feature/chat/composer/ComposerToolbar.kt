package app.manyak.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.core.domain.chat.ChatInputMode
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/** 컴포저 아래 줄. 왼쪽부터 추가 버튼·설정 메뉴이고 전송만 오른쪽 끝이다. */
@Composable
internal fun ComposerToolbar(
    mode: ChatInputMode,
    choicesEnabled: Boolean,
    enabled: Boolean,
    sendState: SendButtonState,
    actions: ChatComposerActions,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ComposerChipButton(
            text = stringResource(R.string.chat_composer_add_situation),
            enabled = enabled,
            // 블럭 모드는 칸을 하나 늘리고, 일반 모드는 고른 구간을 강조 마커로 감싼다.
            onClick = {
                if (mode == ChatInputMode.BLOCK) {
                    actions.onAddBlock(InputBlockType.SITUATION)
                } else {
                    actions.onInsertEmphasis()
                }
            },
        )
        if (mode == ChatInputMode.BLOCK) {
            ComposerChipButton(
                text = stringResource(R.string.chat_composer_add_dialogue),
                enabled = enabled,
                onClick = { actions.onAddBlock(InputBlockType.DIALOGUE) },
            )
        }
        ComposerMenu(
            iconRes = R.drawable.ic_pen_sparkle,
            contentDescription = stringResource(R.string.chat_composer_choices_menu),
            options = choicesOptions(),
            selected = choicesEnabled,
            onSelect = actions.onChoicesEnabledChange,
            // 켜져 있음을 아이콘 색으로 알린다 — 메뉴를 열지 않아도 지금 상태가 보인다.
            tint = if (choicesEnabled) ManyakTheme.colors.textBrand else ManyakTheme.colors.textSubtle,
            enabled = enabled,
        )
        ComposerMenu(
            iconRes = R.drawable.ic_gear,
            contentDescription = stringResource(R.string.chat_composer_input_mode_menu),
            options = inputModeOptions(),
            selected = mode,
            onSelect = actions.onModeChange,
            enabled = enabled,
        )
        // 남은 자리를 밀어내 전송만 오른쪽 끝에 세운다.
        Spacer(modifier = Modifier.weight(1f))
        ComposerSendButton(state = sendState, onClick = onSend)
    }
}

@Composable
private fun choicesOptions(): List<ComposerMenuOption<Boolean>> =
    listOf(
        ComposerMenuOption(
            value = true,
            label = stringResource(R.string.chat_composer_choices_on),
            description = stringResource(R.string.chat_composer_choices_on_description),
        ),
        ComposerMenuOption(
            value = false,
            label = stringResource(R.string.chat_composer_choices_off),
            description = stringResource(R.string.chat_composer_choices_off_description),
        ),
    )

@Composable
private fun inputModeOptions(): List<ComposerMenuOption<ChatInputMode>> =
    listOf(
        ComposerMenuOption(
            value = ChatInputMode.BLOCK,
            label = stringResource(R.string.chat_composer_input_mode_block),
            description = stringResource(R.string.chat_composer_input_mode_block_description),
        ),
        ComposerMenuOption(
            value = ChatInputMode.PLAIN,
            label = stringResource(R.string.chat_composer_input_mode_plain),
            description = stringResource(R.string.chat_composer_input_mode_plain_description),
        ),
    )

/** 내용이 있는 블럭을 지울 때만 뜬다. 빈 블럭은 묻지 않고 바로 지운다. */
@Composable
internal fun RemoveBlockDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.chat_composer_remove_block_title)) },
        text = { Text(text = stringResource(R.string.chat_composer_remove_block_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.chat_composer_remove_block_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.chat_composer_remove_block_cancel))
            }
        },
        containerColor = ManyakTheme.colors.surfaceRaised,
        titleContentColor = ManyakTheme.colors.text,
        textContentColor = ManyakTheme.colors.textSubtle,
        shape = ManyakTheme.shapes.card,
    )
}
