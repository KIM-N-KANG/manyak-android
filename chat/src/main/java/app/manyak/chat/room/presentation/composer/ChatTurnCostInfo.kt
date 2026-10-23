package app.manyak.chat.room.presentation.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.entity.credit.Trials
import app.manyak.common.presentation.credit.LocalCreditPolicy
import app.manyak.common.presentation.credit.LocalTrials
import app.manyak.common.presentation.credit.creditAmountText
import app.manyak.designsystem.component.ManyakPopup
import app.manyak.designsystem.credit.CreditAmountText
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 전송 버튼 왼쪽의 한 턴 비용 버튼. 누르면 위로 이프 안내 팝오버를 열어 항목별 비용, 남은 무료 체험, 합계를
 * 영수증처럼 보인다. 버튼 수치와 팝오버 합계는 같은 [chatTurnCost] 로 계산해 어긋나지 않는다.
 *
 * @param modelName 지금 고른 모델 이름. AI 응답 줄 옆에 보조 글씨로 붙는다.
 * @param longResponseEnabled 긴 응답이 켜져 있으면 모델 이름 뒤에 함께 적는다. 비용은 바뀌지 않는다.
 * @param modelCode 지금 고른 모델 코드. 턴 체험은 체험 모델에만 적용한다.
 */
@Composable
internal fun ChatTurnCostButton(
    turnCost: Long?,
    realtimeImageEnabled: Boolean,
    modelName: String?,
    longResponseEnabled: Boolean,
    modelCode: String?,
    modifier: Modifier = Modifier,
) {
    val policy = LocalCreditPolicy.current
    val trials = LocalTrials.current
    val cost = chatTurnCost(turnCost, modelCode, policy, trials, realtimeImageEnabled)
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .clip(ManyakTheme.shapes.menuItem)
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = ManyakTheme.spacing.dense, vertical = ManyakTheme.spacing.inline),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreditAmountText(
                amount = stringResource(ChatR.string.chat_composer_turn_credit_cost, creditAmountText(cost.discounted)),
                fullAmount = cost.full?.takeIf { cost.isDiscounted }?.let(::creditAmountText),
                pending = cost.discounted == null,
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
            // 옆 비용 글자(12sp)와 같은 높이로 두어 아이콘이 수치보다 도드라지지 않게 한다.
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.iconTiny),
                painter = painterResource(DesignsystemR.drawable.ic_info),
                contentDescription = stringResource(ChatR.string.chat_turn_cost_info_open),
                tint = ManyakTheme.colors.textSubtle,
            )
        }
        ChatTurnCostPopover(
            open = open,
            onDismiss = { open = false },
            cost = cost,
            turnCost = turnCost,
            policy = policy,
            trials = trials,
            realtimeImageEnabled = realtimeImageEnabled,
            modelName = modelName,
            longResponseEnabled = longResponseEnabled,
            modelCode = modelCode,
        )
    }
}

/** 앵커 오른쪽 끝에 맞춰 위로 여는 팝오버. 컴포저가 화면 아래에 붙어 있어 아래에는 자리가 없다. */
@Composable
private fun ChatTurnCostPopover(
    open: Boolean,
    onDismiss: () -> Unit,
    cost: ChatTurnCost,
    turnCost: Long?,
    policy: CreditPolicy?,
    trials: Trials?,
    realtimeImageEnabled: Boolean,
    modelName: String?,
    longResponseEnabled: Boolean,
    modelCode: String?,
) {
    val gapPx = with(LocalDensity.current) { ManyakTheme.spacing.compact.roundToPx() }
    val positionProvider = remember(gapPx) { AboveEndPositionProvider(gapPx) }
    ManyakPopup(
        visible = open,
        transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f),
        slideFromBelow = true,
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ChatTurnCostPanel(
            cost = cost,
            turnCost = turnCost,
            policy = policy,
            trials = trials,
            realtimeImageEnabled = realtimeImageEnabled,
            modelName = modelName,
            longResponseEnabled = longResponseEnabled,
            modelCode = modelCode,
        )
    }
}

private class AboveEndPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            x = (anchorBounds.right - popupContentSize.width).coerceIn(0, windowSize.width - popupContentSize.width),
            y = anchorBounds.top - popupContentSize.height - gapPx,
        )
}

/**
 * 팝오버 판. 항목을 먼저 적고 구분선 아래에 합계를 둔다.
 * 판 모양은 정렬·셀렉트 메뉴(`ManyakSelectMenu`)와 같다 — 같은 화면에서 뜨는 판끼리 모양이 갈리지 않게 한다.
 */
@Composable
private fun ChatTurnCostPanel(
    cost: ChatTurnCost,
    turnCost: Long?,
    policy: CreditPolicy?,
    trials: Trials?,
    realtimeImageEnabled: Boolean,
    modelName: String?,
    longResponseEnabled: Boolean,
    modelCode: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(PanelWidth)
                .shadow(elevation = PanelShadowElevation, shape = ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                .border(PanelBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                .clip(ManyakTheme.shapes.control)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Text(
            text = stringResource(ChatR.string.chat_turn_cost_info_title),
            style = ManyakTheme.typography.bodyMediumStrong,
            color = ManyakTheme.colors.text,
        )
        CostRow(
            label = stringResource(ChatR.string.chat_turn_cost_info_turn),
            detail =
                modelName?.let { name ->
                    if (longResponseEnabled) {
                        stringResource(ChatR.string.chat_turn_cost_info_model_long_response, name)
                    } else {
                        name
                    }
                },
            cost = turnCost,
            trial = trials?.chatTurnFor(modelCode),
        )
        if (realtimeImageEnabled) {
            CostRow(
                label = stringResource(ChatR.string.chat_settings_realtime_image),
                cost = policy?.chatImageCost,
                trial = trials?.chatImage,
            )
        }
        TotalRow(cost = cost)
    }
}

/** 구분선 아래의 합계 줄. 전송 버튼 옆 수치와 같은 값을 더 크게 보인다. */
@Composable
private fun TotalRow(
    cost: ChatTurnCost,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PanelBorderWidth)
                    .background(ManyakTheme.colors.border),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(ChatR.string.chat_turn_cost_info_total),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
            )
            CreditAmountText(
                amount = stringResource(ChatR.string.chat_composer_turn_credit_cost, creditAmountText(cost.discounted)),
                fullAmount = cost.full?.takeIf { cost.isDiscounted }?.let(::creditAmountText),
                pending = cost.discounted == null,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
        }
    }
}

/**
 * 비용 내역 한 줄. 라벨 아래에 남은 무료 체험을 적고, 체험이 남은 항목은 정가에 취소선을 긋고 0 을 보인다.
 * 체험 줄은 브랜드 색으로 금액과 이어 읽히게 하고, 체험을 다 쓴 항목은 정가만 보이면 되므로 그리지 않는다.
 */
@Composable
private fun CostRow(
    label: String,
    cost: Long?,
    trial: TrialUsage?,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val isFree = trial?.isFree == true
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense)) {
                Text(text = label, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.text)
                if (detail != null) {
                    Text(
                        text = detail,
                        style = ManyakTheme.typography.bodyMedium,
                        color = ManyakTheme.colors.textSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trial?.takeIf { isFree }?.let {
                Text(
                    text = trialText(it),
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textBrand,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFree) {
                Text(
                    text = creditAmountText(cost),
                    style = ManyakTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                    color = ManyakTheme.colors.textSubtlest,
                )
            }
            Text(
                text =
                    stringResource(
                        ChatR.string.chat_composer_turn_credit_cost,
                        creditAmountText(if (isFree) 0 else cost),
                    ),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
            )
        }
    }
}

@Composable
private fun trialText(trial: TrialUsage): String {
    val remaining = trial.remaining
    return when {
        remaining == null -> stringResource(ChatR.string.chat_turn_cost_info_trial_unlimited)
        else -> stringResource(ChatR.string.chat_turn_cost_info_trial_remaining, remaining)
    }
}

private val PanelWidth = 256.dp
private val PanelShadowElevation = 4.dp
private val PanelBorderWidth = 1.dp

@Preview(showBackground = true, name = "이프 안내, 턴 체험 남음, 이미지 체험 다 씀, 긴 응답")
@Composable
private fun ChatTurnCostPanelPreview() {
    ManyakTheme(darkTheme = false) {
        val trials =
            Trials(chatTurn = TrialUsage(used = 2, limit = 5), chatImage = TrialUsage(used = 5, limit = 5))
        val policy = CreditPolicy(chatImageCost = 30)
        ChatTurnCostPanel(
            cost = chatTurnCost(50, CHAT_TRIAL_MODEL_CODE, policy, trials, withRealtimeImage = true),
            turnCost = 50,
            policy = policy,
            trials = trials,
            realtimeImageEnabled = true,
            modelName = "라이트",
            longResponseEnabled = true,
            modelCode = CHAT_TRIAL_MODEL_CODE,
        )
    }
}
