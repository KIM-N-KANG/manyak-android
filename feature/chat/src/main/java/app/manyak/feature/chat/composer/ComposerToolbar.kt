package app.manyak.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.core.domain.chat.ChatInputMode
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakDestructiveDialog
import app.manyak.core.ui.theme.ManyakTheme

/** 컴포저 아래 줄. 왼쪽부터 추가 버튼·설정 메뉴이고 턴 비용과 전송만 오른쪽 끝이다. */
@Composable
internal fun ComposerToolbar(
    mode: ChatInputMode,
    choicesEnabled: Boolean,
    canAddBlock: Boolean,
    enabled: Boolean,
    sendState: SendButtonState,
    actions: ChatComposerActions,
    onInsertEmphasis: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val blockMode = mode == ChatInputMode.BLOCK
        ComposerChipButton(
            text = stringResource(R.string.chat_composer_add_situation),
            // 일반 모드에서는 칸이 늘지 않으므로 개수 상한과 무관하다.
            enabled = enabled && (!blockMode || canAddBlock),
            // 블럭 모드는 칸을 하나 늘리고, 일반 모드는 고른 구간을 강조 마커로 감싼다.
            onClick = {
                if (mode == ChatInputMode.BLOCK) {
                    actions.onAddBlock(InputBlockType.SITUATION)
                } else {
                    onInsertEmphasis()
                }
            },
        )
        if (blockMode) {
            ComposerChipButton(
                text = stringResource(R.string.chat_composer_add_dialogue),
                enabled = enabled && canAddBlock,
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
        // 남은 자리를 밀어내 비용과 전송만 오른쪽 끝에 세운다.
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 전송 버튼 상태(전송·랜덤·대기)가 바뀌어도 자리가 그대로다.
            Text(
                text = stringResource(R.string.chat_composer_turn_credit_cost),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
            ComposerSendButton(state = sendState, onClick = onSend)
        }
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
    ManyakDestructiveDialog(
        title = stringResource(R.string.chat_composer_remove_block_title),
        description = stringResource(R.string.chat_composer_remove_block_description),
        confirmLabel = stringResource(R.string.chat_composer_remove_block_confirm),
        cancelLabel = stringResource(R.string.chat_composer_remove_block_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
