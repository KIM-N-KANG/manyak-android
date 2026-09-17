package app.manyak.chat.room.presentation.composer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.chat.entity.ChatInputMode
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakSwitch
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 채팅 설정 시트. 스위치를 바꾸면 곧바로 반영되고 시트는 열린 채 남는다 — 블럭 입력을 끄면 시트 뒤의
 * 입력창이 일반 입력으로 바뀌는 것이 보인다.
 *
 * 열림 상태는 화면이 든다. 컴포저 안에 두면 입력 모드가 바뀌어 컴포저가 갈릴 때 시트도 같이 사라진다.
 */
@Composable
internal fun ChatSettingsSheet(
    realtimeImageEnabled: Boolean,
    choicesEnabled: Boolean,
    mode: ChatInputMode,
    onRealtimeImageEnabledChange: (Boolean) -> Unit,
    onChoicesEnabledChange: (Boolean) -> Unit,
    onModeChange: (ChatInputMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        Text(
            text = stringResource(ChatR.string.chat_settings_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        ChatSettingsGroup(labelRes = ChatR.string.chat_settings_group_features) {
            ChatSettingRow(
                iconRes = DesignsystemR.drawable.ic_ai_image,
                labelRes = ChatR.string.chat_settings_realtime_image,
                descriptionRes = ChatR.string.chat_settings_realtime_image_description,
                checked = realtimeImageEnabled,
                onCheckedChange = onRealtimeImageEnabledChange,
            )
            ChatSettingRow(
                iconRes = DesignsystemR.drawable.ic_pen_sparkle,
                labelRes = ChatR.string.chat_settings_choices,
                descriptionRes = ChatR.string.chat_settings_choices_description,
                checked = choicesEnabled,
                onCheckedChange = onChoicesEnabledChange,
            )
        }
        ChatSettingsGroup(labelRes = ChatR.string.chat_settings_group_input_mode) {
            ChatSettingRow(
                iconRes = DesignsystemR.drawable.ic_form,
                labelRes = ChatR.string.chat_settings_block_input,
                descriptionRes = ChatR.string.chat_settings_block_input_description,
                checked = mode == ChatInputMode.BLOCK,
                onCheckedChange = { checked ->
                    onModeChange(if (checked) ChatInputMode.BLOCK else ChatInputMode.PLAIN)
                },
            )
        }
        ManyakTextButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
            onClick = onDismiss,
        ) {
            Text(
                text = stringResource(ChatR.string.chat_settings_close),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun ChatSettingsGroup(
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(bottom = ManyakTheme.spacing.compact),
            text = stringResource(labelRes),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.text,
        )
        content()
    }
}

/**
 * 마이 메뉴와 같은 배치의 토글 한 줄. **행 전체가 스위치다** — 선택 표시가 곧 피드백이라 리플을 두지 않고,
 * 스위치는 표시만 맡아 한 번의 탭이 두 번 토글되지 않게 한다.
 */
@Composable
private fun ChatSettingRow(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .toggleable(
                    value = checked,
                    interactionSource = null,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(vertical = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            tint = ManyakTheme.colors.text,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(labelRes),
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.text,
            )
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        ManyakSwitch(checked = checked, onCheckedChange = null)
    }
}

@Preview(name = "채팅 설정 · 라이트")
@Composable
private fun ChatSettingsSheetPreview() {
    ManyakTheme(darkTheme = false) {
        ChatSettingsSheet(
            realtimeImageEnabled = true,
            choicesEnabled = true,
            mode = ChatInputMode.BLOCK,
            onRealtimeImageEnabledChange = {},
            onChoicesEnabledChange = {},
            onModeChange = {},
            onDismiss = {},
        )
    }
}
