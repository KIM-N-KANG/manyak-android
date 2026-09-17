package app.manyak.chat.room.presentation.composer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.chat.entity.ChatInputMode
import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.presentation.credit.LocalCreditPolicy
import app.manyak.common.presentation.credit.LocalTrials
import app.manyak.common.presentation.credit.creditAmountText
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakSwitch
import app.manyak.designsystem.credit.CreditAmountText
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 채팅 설정 시트. 스위치를 바꾸면 곧바로 반영되고 시트는 열린 채 남는다 — 블럭 입력을 끄면 시트 뒤의
 * 입력창이 일반 입력으로 바뀌는 것이 보인다. 확정할 것이 없어 닫기 버튼을 두지 않고 스크림·끌어내리기·
 * 뒤로가기로만 닫는다.
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
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
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
                labelAddon = { RealtimeImageCostBadge(imageTrial = LocalTrials.current?.chatImage) },
            )
            ChatSettingRow(
                iconRes = DesignsystemR.drawable.ic_ai_chat,
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
 * 실시간 이미지 비용. 이미지 체험이 남았으면 정가에 취소선을 긋고 0 을 보인다.
 *
 * 체험을 다 쓴 뒤에는 왼쪽에 안내 버튼이 붙는다 — 이미지가 안 만들어져도 이프가 나가는지 묻는 질문에 팝오버로
 * 답한다. 체험 중에는 이프가 나가지 않아 물을 것이 없다.
 */
@Composable
private fun RealtimeImageCostBadge(imageTrial: TrialUsage?) {
    val chatImageCost = LocalCreditPolicy.current?.chatImageCost
    val isFree = imageTrial?.isFree == true
    if (imageTrial != null && !isFree) RealtimeImageNoticeButton()
    CreditAmountText(
        modifier =
            Modifier
                // 라벨 줄의 간격은 안내 버튼에 맞춘 좁은 값이라 배지는 제 몫을 더해 한 단계 띄운다.
                .padding(start = ManyakTheme.spacing.dense)
                .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.pill)
                .padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.hairline),
        amount =
            stringResource(
                ChatR.string.chat_composer_turn_credit_cost,
                creditAmountText(if (isFree) 0 else chatImageCost),
            ),
        fullAmount = chatImageCost?.takeIf { isFree }?.let(::creditAmountText),
        pending = chatImageCost == null || imageTrial == null,
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textSubtle,
    )
}

@Composable
private fun RealtimeImageNoticeButton() {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        ManyakIconButton(
            iconRes = DesignsystemR.drawable.ic_info,
            contentDescription = stringResource(ChatR.string.chat_settings_realtime_image_notice),
            onClick = { open = true },
            size = NoticeButtonSize,
            iconSize = ManyakTheme.sizes.iconSmall,
            tint = ManyakTheme.colors.textSubtle,
        )
        if (open) NoticePopover(onDismiss = { open = false })
    }
}

/** 앵커 왼쪽 끝에 맞춰 아래로 여는 한 문장 팝오버. 시트 안에 있어 창 밖으로 나갈 오른쪽 여유가 없다. */
@Composable
private fun NoticePopover(onDismiss: () -> Unit) {
    val gapPx = with(LocalDensity.current) { ManyakTheme.spacing.inline.roundToPx() }
    val positionProvider =
        remember(gapPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset =
                    IntOffset(
                        x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
                        y = anchorBounds.bottom + gapPx,
                    )
            }
        }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Text(
            modifier =
                Modifier
                    .widthIn(max = NoticePopoverMaxWidth)
                    .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                    .border(NoticePopoverBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                    .padding(
                        horizontal = ManyakTheme.spacing.component,
                        vertical = ManyakTheme.spacing.compact,
                    ),
            text = stringResource(ChatR.string.chat_settings_realtime_image_notice_message),
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.text,
        )
    }
}

/**
 * 마이 메뉴와 같은 배치의 토글 한 줄. **행 전체가 스위치다** — 선택 표시가 곧 피드백이라 리플을 두지 않고,
 * 스위치는 표시만 맡아 한 번의 탭이 두 번 토글되지 않게 한다.
 *
 * @param labelAddon 라벨 오른쪽에 붙는 보조 표시(비용 배지 등).
 */
@Composable
private fun ChatSettingRow(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    labelAddon: (@Composable () -> Unit)? = null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // 스토리 상세의 엔딩 라벨과 안내 버튼 사이와 같은 간격이다.
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = ManyakTheme.typography.bodyLarge,
                    color = ManyakTheme.colors.text,
                )
                labelAddon?.invoke()
            }
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        ManyakSwitch(checked = checked, onCheckedChange = null)
    }
}

/** 스토리 상세의 엔딩 안내 버튼과 같은 크기 — 라벨 글줄 높이라 줄이 두꺼워지지 않는다. */
private val NoticeButtonSize = 24.dp

private val NoticePopoverMaxWidth = 256.dp

private val NoticePopoverBorderWidth = 1.dp

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
