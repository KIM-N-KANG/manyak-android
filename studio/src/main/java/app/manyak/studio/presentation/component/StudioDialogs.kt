package app.manyak.studio.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakOptionItem
import app.manyak.designsystem.component.ManyakOptionsSheet
import app.manyak.designsystem.component.ManyakOptionsSheetHeader
import app.manyak.report.presentation.StoryReportAction
import app.manyak.report.presentation.component.StoryReportSheet
import app.manyak.studio.presentation.StudioCard
import app.manyak.studio.presentation.StudioIntent
import app.manyak.studio.presentation.StudioUiState
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR
import app.manyak.studio.R as StudioR

/** 제작 탭이 본문 위에 띄우는 것들 — 카드 옵션 시트, 삭제 확인 다이얼로그, 신고 시트. 본문 배치와 섞이지 않게 따로 둔다. */
@Composable
internal fun StudioDialogs(
    state: StudioUiState,
    onIntent: (StudioIntent) -> Unit,
) {
    state.optionsTarget?.let { card -> CardOptionsSheet(card = card, onIntent = onIntent) }

    state.deleteTarget?.let { target ->
        // 로컬 초안·요청은 서버 스토리가 아니라 잃는 것이 다르므로 문구를 나눈다.
        val isStory = target is StudioCard.Story
        ManyakDestructiveDialog(
            title =
                stringResource(
                    if (isStory) {
                        CommonR.string.studio_delete_dialog_title
                    } else {
                        StudioR.string.studio_progress_delete_dialog_title
                    },
                ),
            description =
                stringResource(
                    if (isStory) {
                        CommonR.string.studio_delete_dialog_description
                    } else {
                        StudioR.string.studio_progress_delete_dialog_description
                    },
                ),
            confirmLabel = stringResource(CommonR.string.studio_story_delete),
            cancelLabel = stringResource(CommonR.string.studio_delete_dialog_cancel),
            onConfirm = { onIntent(StudioIntent.ConfirmDelete) },
            onDismiss = { onIntent(StudioIntent.DismissDeleteDialog) },
            inProgress = state.isDeleting,
            inProgressLabel = stringResource(DesignsystemR.string.delete_in_progress),
        )
    }

    if (state.report.isSheetOpen) {
        StoryReportSheet(
            state = state.report,
            onAction = { action -> onIntent(StudioIntent.Report(action)) },
        )
    }
}

/** 스토리 카드는 신고·삭제, 초안·실패 요청 카드는 삭제만 있다. 완성 중 카드는 옵션 자체가 없다. */
@Composable
private fun CardOptionsSheet(
    card: StudioCard,
    onIntent: (StudioIntent) -> Unit,
) {
    ManyakOptionsSheet(
        onDismissRequest = { onIntent(StudioIntent.CloseCardOptions) },
        header = {
            ManyakOptionsSheetHeader(
                kind =
                    stringResource(
                        if (card is StudioCard.Story) {
                            StudioR.string.studio_card_kind_story
                        } else {
                            StudioR.string.studio_card_kind_progress
                        },
                    ),
                title =
                    when (card) {
                        is StudioCard.Story -> card.story.title
                        is StudioCard.Draft -> stringResource(StudioR.string.studio_progress_draft_title)
                        is StudioCard.FailedRequest -> stringResource(StudioR.string.studio_progress_failed_title)
                    },
            )
        },
    ) {
        if (card is StudioCard.Story) {
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_alert_triangle,
                label = stringResource(ReportR.string.story_report_action),
                onClick = { onIntent(StudioIntent.Report(StoryReportAction.Open)) },
            )
        }
        ManyakOptionItem(
            iconRes = DesignsystemR.drawable.ic_delete,
            label = stringResource(CommonR.string.studio_story_delete),
            onClick = { onIntent(StudioIntent.RequestDelete) },
            isDestructive = true,
        )
    }
}
