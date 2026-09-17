package app.manyak.chat.room.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakOptionItem
import app.manyak.designsystem.component.ManyakOptionsSheet
import app.manyak.designsystem.component.ManyakOptionsSheetHeader
import app.manyak.designsystem.credit.CreditBalanceCard
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.report.presentation.StoryReportAction
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR

/**
 * 헤더 더보기가 여는 채팅 메뉴. 내 이프 카드 → 새 채팅 시작하기 → 공유하기 → 신고하기 → 삭제하기 순서다.
 *
 * 신고·삭제·충전은 다른 화면이나 모달을 여는 항목이라 시트를 먼저 닫는다 — 시트는 별도 창이라
 * 닫지 않고 이동하면 다음 화면 위에 그대로 남는다. 새 채팅·공유는 요청이 끝날 때까지 시트가 남아
 * 진행을 보이고, 그동안은 끌어내려도 닫히지 않는다.
 *
 * 참조 스토리가 삭제된 방([ChatRoomUiState.hasStory])에는 새 채팅·신고를 두지 않는다.
 */
@Composable
internal fun ChatMenuSheet(
    state: ChatRoomUiState,
    onIntent: (ChatRoomIntent) -> Unit,
    onDelete: () -> Unit,
    onOpenCreditCharge: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakOptionsSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        dismissEnabled = !state.isStartingNewChat && !state.isSharing,
        header = { ManyakOptionsSheetHeader(title = stringResource(ChatR.string.chat_room_menu)) },
    ) {
        CreditBalanceCard(
            balance = state.creditBalance,
            onOpenCharge = {
                onDismiss()
                onOpenCreditCharge()
            },
            modifier = Modifier.padding(bottom = ManyakTheme.spacing.gutter),
        )
        if (state.hasStory) {
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_comment_plus,
                label = stringResource(ChatR.string.chat_room_new_chat),
                onClick = { onIntent(ChatRoomIntent.NewChatRequested) },
                inProgress = state.isStartingNewChat,
            )
        }
        ManyakOptionItem(
            iconRes = DesignsystemR.drawable.ic_share,
            label = stringResource(ChatR.string.chat_room_share),
            onClick = { onIntent(ChatRoomIntent.ShareRequested) },
            inProgress = state.isSharing,
        )
        if (state.hasStory) {
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_alert_triangle,
                label = stringResource(ReportR.string.story_report_action),
                onClick = {
                    onDismiss()
                    onIntent(ChatRoomIntent.Report(StoryReportAction.Open))
                },
            )
        }
        ManyakOptionItem(
            iconRes = DesignsystemR.drawable.ic_delete,
            label = stringResource(ChatR.string.chat_room_delete),
            onClick = {
                onDismiss()
                onDelete()
            },
            isDestructive = true,
        )
    }
}
