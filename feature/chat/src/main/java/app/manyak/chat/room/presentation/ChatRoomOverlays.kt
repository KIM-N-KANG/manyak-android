package app.manyak.chat.room.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.manyak.chat.room.presentation.tour.ChatTourOverlay
import app.manyak.chat.room.presentation.tour.ChatTourTargets
import app.manyak.chat.room.presentation.tour.chatTourSteps
import app.manyak.common.presentation.credit.CreditShortageUiState
import app.manyak.designsystem.component.ManyakConfirmDialog
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.credit.CreditShortageSheet
import app.manyak.report.presentation.StoryReportUiState
import app.manyak.report.presentation.component.StoryReportSheet
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

/** 삭제 확인. 메뉴 시트를 닫은 뒤에 뜬다 — 시트 위에 다이얼로그를 겹치면 바깥 탭 판정이 서로 얽힌다. */
@Composable
internal fun ChatRoomDeleteDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ManyakDestructiveDialog(
        title = stringResource(ChatR.string.chat_room_delete_dialog_title),
        description = stringResource(ChatR.string.chat_room_delete_dialog_description),
        confirmLabel = stringResource(ChatR.string.chat_room_delete),
        cancelLabel = stringResource(ChatR.string.chat_room_delete_dialog_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        inProgress = isDeleting,
        inProgressLabel = stringResource(DesignsystemR.string.delete_in_progress),
    )
}

/** 상태가 여닫는 다이얼로그와 시트. 화면 본체가 길어지지 않게 모아 둔다. */
@Composable
internal fun ChatRoomStateOverlays(
    state: ChatRoomUiState,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    ChatRoomTurnConfirmDialog(confirm = state.turnConfirm, onIntent = onIntent)
    ChatRoomReportSheet(state = state.report, onIntent = onIntent)
    ChatRoomCreditShortageSheet(state = state.creditShortage, onIntent = onIntent)
}

/**
 * 재생성, 입력 수정과 포크의 확인. 입력 수정은 뒤의 턴이 사라질 때만 위험색이고 마지막 턴이면 재생성처럼
 * 응답만 바뀐다고 알린다. 머무르는 쪽은 앱의 다른 확인 창과 같이 "닫기"다.
 */
@Composable
private fun ChatRoomTurnConfirmDialog(
    confirm: TurnConfirm?,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    if (confirm == null) return
    val onConfirm = { onIntent(ChatRoomIntent.TurnConfirmAccepted) }
    val onDismiss = { onIntent(ChatRoomIntent.TurnConfirmDismissed) }
    when (confirm) {
        is TurnConfirm.Regenerate ->
            ManyakConfirmDialog(
                title = stringResource(ChatR.string.chat_room_regenerate_confirm_title),
                description = stringResource(ChatR.string.chat_room_response_replaced),
                confirmLabel = stringResource(ChatR.string.chat_room_regenerate_confirm_confirm),
                cancelLabel = stringResource(ChatR.string.chat_room_turn_confirm_cancel),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )

        is TurnConfirm.Edit ->
            ManyakConfirmDialog(
                title = stringResource(ChatR.string.chat_room_edit_confirm_title),
                description =
                    if (confirm.laterTurnCount > 0) {
                        stringResource(ChatR.string.chat_room_edit_confirm_later_turns, confirm.laterTurnCount)
                    } else {
                        stringResource(ChatR.string.chat_room_response_replaced)
                    },
                confirmLabel = stringResource(ChatR.string.chat_room_edit_confirm_confirm),
                cancelLabel = stringResource(ChatR.string.chat_room_turn_confirm_cancel),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                destructive = confirm.laterTurnCount > 0,
            )

        is TurnConfirm.Fork ->
            ManyakConfirmDialog(
                title = stringResource(ChatR.string.chat_room_fork_confirm_title),
                description = stringResource(ChatR.string.chat_room_fork_confirm_description),
                confirmLabel = stringResource(ChatR.string.chat_room_fork_confirm_confirm),
                cancelLabel = stringResource(ChatR.string.chat_room_turn_confirm_cancel),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
    }
}

/** 신고 시트. 화면 본체가 길어지지 않게 따로 둔다. */
@Composable
internal fun ChatRoomReportSheet(
    state: StoryReportUiState,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    if (!state.isSheetOpen) return
    StoryReportSheet(
        state = state,
        onAction = { action -> onIntent(ChatRoomIntent.Report(action)) },
    )
}

/** 이프 부족 시트. 전송, 재생성과 입력 수정의 잔액 선검사나 402 에서 열린다. */
@Composable
internal fun ChatRoomCreditShortageSheet(
    state: CreditShortageUiState?,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    if (state == null) return
    CreditShortageSheet(
        attendanceVisible = state.canClaimAttendance,
        isClaimingAttendance = state.isClaimingAttendance,
        isCharging = state.isCharging,
        onCharge = { onIntent(ChatRoomIntent.CreditShortageCharge) },
        onClaimAttendance = { onIntent(ChatRoomIntent.CreditShortageAttendance) },
        onDismiss = { onIntent(ChatRoomIntent.CreditShortageDismissed) },
    )
}

/** 첫 진입 안내 투어. 방을 연 뒤에만 열린다. */
@Composable
internal fun ChatRoomTour(
    state: ChatRoomUiState,
    targets: ChatTourTargets,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    if (!state.tourOpen) return
    ChatTourOverlay(
        steps = chatTourSteps(state.composer.mode),
        stepIndex = state.tourStep,
        targets = targets,
        onStepShown = { index, step -> onIntent(ChatRoomIntent.TourStepShown(index, step)) },
        onComplete = { onIntent(ChatRoomIntent.TourCompleted) },
        onSkip = { index -> onIntent(ChatRoomIntent.TourSkipped(index)) },
    )
}

/** 투어가 딤으로 덮은 화면은 보조기술도 읽지 않는다. 조작이 막힌 버튼을 읽어 주면 누를 수 있는 것처럼 들린다. */
internal fun Modifier.hiddenBehindTour(state: ChatRoomUiState): Modifier =
    if (state.tourOpen) clearAndSetSemantics {} else this
