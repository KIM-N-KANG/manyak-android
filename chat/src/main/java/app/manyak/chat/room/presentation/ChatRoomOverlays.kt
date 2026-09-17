package app.manyak.chat.room.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakDestructiveDialog
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
