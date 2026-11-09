package app.manyak.chat.room.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.chat.entity.ChatModel
import app.manyak.chat.room.presentation.composer.ChatSettingRow
import app.manyak.chat.room.presentation.composer.ChatSettingsGroup
import app.manyak.chat.room.presentation.composer.chatTurnCost
import app.manyak.common.presentation.credit.LocalCreditPolicy
import app.manyak.common.presentation.credit.LocalTrials
import app.manyak.common.presentation.credit.creditAmountText
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.credit.CreditAmountText
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 헤더의 AI 모델 선택 버튼. 지금 모델 이름만 보이고 비용은 전송 버튼 옆 배지가 맡는다.
 * 응답을 받는 중에도 누를 수 있다 — 바꾼 모델은 다음 전송부터 적용된다.
 */
@Composable
internal fun ChatModelButton(
    model: ChatModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = model.name
    val description = stringResource(ChatR.string.chat_model_open, label)
    Row(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.controlCompact)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.backgroundNeutral)
                .border(1.dp, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                .clickable(role = Role.Button, onClick = onClick)
                // 보조기술은 "AI 모델 선택" 다음에 지금 모델 이름을 읽는다.
                .clearAndSetSemantics { contentDescription = description }
                .padding(start = ManyakTheme.spacing.controlHorizontal, end = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense),
    ) {
        Text(text = label, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
        Icon(
            painter = painterResource(DesignsystemR.drawable.ic_chevron_down),
            contentDescription = null,
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 채팅 응답 시트. 모델 칸을 누르면 시트를 닫고 그 모델을 적용하며, 이미 고른 칸을 눌러도 닫는다 —
 * 칸을 고르는 것 외에 확정할 것이 없어 확정 버튼을 두지 않는다. 긴 응답 스위치는 채팅 설정처럼 바로
 * 반영되고 시트는 열린 채 남는다.
 */
@Composable
internal fun ChatModelSheet(
    models: List<ChatModel>,
    selected: String?,
    longResponseEnabled: Boolean,
    onSelect: (String) -> Unit,
    onLongResponseEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.section),
    ) {
        Text(
            text = stringResource(ChatR.string.chat_model_title),
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
        )
        ChatSettingsGroup(labelRes = ChatR.string.chat_model_group) {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                models.forEach { model ->
                    ChatModelOption(
                        model = model,
                        selected = model.code == selected,
                        onClick = { onSelect(model.code) },
                    )
                }
            }
        }
        ChatSettingsGroup(labelRes = ChatR.string.chat_response_length_group) {
            ChatSettingRow(
                iconRes = DesignsystemR.drawable.ic_ruler,
                labelRes = ChatR.string.chat_response_long,
                descriptionRes = ChatR.string.chat_response_long_description,
                checked = longResponseEnabled,
                onCheckedChange = onLongResponseEnabledChange,
            )
        }
    }
}

/** 모델 한 칸. 고른 칸은 키워드 칩과 같은 선택 표현을 쓴다. */
@Composable
private fun ChatModelOption(
    model: ChatModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ManyakTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ManyakTheme.shapes.overlay)
                .background(if (selected) colors.backgroundBrandSubtle else colors.surfaceRaised)
                .border(1.dp, if (selected) colors.borderBrand else colors.border, ManyakTheme.shapes.overlay)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            Text(
                text = model.name,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
            ChatModelCostBadge(model = model, selected = selected)
        }
        modelDescription(model.code)?.let { descriptionRes ->
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

/**
 * 그 칸의 모델이 한 턴에 쓰는 이프. 모델끼리 비교하는 자리라 실시간 이미지 비용은 더하지 않는다.
 * 설정 시트의 실시간 이미지 배지와 같은 모양이다. 고른 칸의 연한 브랜드 배경 위에서는 회색 캡슐이 탁해 보여
 * 화면 배경색 캡슐에 브랜드 글씨로 바꾼다.
 */
@Composable
private fun ChatModelCostBadge(
    model: ChatModel,
    selected: Boolean,
) {
    val cost =
        chatTurnCost(
            model.turnCost,
            model.code,
            LocalCreditPolicy.current,
            LocalTrials.current,
            withRealtimeImage = false,
        )
    CreditAmountText(
        modifier =
            Modifier
                .background(
                    if (selected) ManyakTheme.colors.surface else ManyakTheme.colors.backgroundNeutral,
                    ManyakTheme.shapes.pill,
                ).padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.hairline),
        amount = stringResource(ChatR.string.chat_composer_turn_credit_cost, creditAmountText(cost.discounted)),
        fullAmount = cost.full?.takeIf { cost.isDiscounted }?.let(::creditAmountText),
        pending = cost.discounted == null,
        style = ManyakTheme.typography.bodySmall,
        color = if (selected) ManyakTheme.colors.textBrand else ManyakTheme.colors.textSubtle,
    )
}

/** 이름과 가격은 서버가 주지만 설명은 앱 문구다. 앱이 모르는 새 모델은 설명 없이 보인다. */
@StringRes
private fun modelDescription(code: String): Int? =
    when (code) {
        "LITE" -> ChatR.string.chat_model_lite_description
        "AURORA" -> ChatR.string.chat_model_aurora_description
        else -> null
    }

private val PreviewModels =
    listOf(
        ChatModel(code = "LITE", name = "라이트", turnCost = 20),
        ChatModel(code = "AURORA", name = "오로라", turnCost = 50),
    )

@Preview(name = "채팅 응답 · 라이트")
@Composable
private fun ChatModelSheetPreview() {
    ManyakTheme(darkTheme = false) {
        ChatModelSheet(
            models = PreviewModels,
            selected = "AURORA",
            longResponseEnabled = false,
            onSelect = {},
            onLongResponseEnabledChange = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "AI 모델 선택 버튼")
@Composable
private fun ChatModelButtonPreview() {
    ManyakTheme(darkTheme = false) {
        ChatModelButton(model = PreviewModels.last(), onClick = {})
    }
}
