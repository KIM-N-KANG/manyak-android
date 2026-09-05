package app.manyak.feature.studio

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.common.entity.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakDestructiveDialogContent
import app.manyak.designsystem.component.ManyakDialog
import app.manyak.designsystem.component.ManyakOptionsDialogContent
import app.manyak.designsystem.component.ManyakOptionsDialogItem
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.report.presentation.StoryReportAction
import app.manyak.report.presentation.component.StoryReportSheet
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR

/** 제작 탭이 본문 위에 띄우는 것들 — 확인 다이얼로그 둘과 카드 옵션·신고 시트. 본문 배치와 섞이지 않게 따로 둔다. */
@Composable
internal fun StudioDialogs(
    state: StudioUiState,
    onIntent: (StudioIntent) -> Unit,
) {
    // 옵션과 삭제 확인은 한 창을 나눠 쓴다 — 창을 닫고 새로 열면 스크림이 두 번 페이드돼 번쩍인다.
    val deleteTarget = state.deleteTarget
    val optionsTarget = state.optionsTarget
    if (optionsTarget != null || deleteTarget != null) {
        ManyakDialog(
            onDismissRequest = {
                onIntent(if (deleteTarget != null) StudioIntent.DismissDeleteDialog else StudioIntent.CloseStoryOptions)
            },
        ) {
            Crossfade(
                targetState = deleteTarget,
                animationSpec = tween(ManyakTheme.motion.elementEnterMillis),
                label = "storyCardDialog",
            ) { target ->
                if (target != null) {
                    ManyakDestructiveDialogContent(
                        title = stringResource(R.string.studio_delete_dialog_title),
                        description = stringResource(R.string.studio_delete_dialog_description),
                        confirmLabel = stringResource(R.string.studio_story_delete),
                        cancelLabel = stringResource(R.string.studio_delete_dialog_cancel),
                        onConfirm = { onIntent(StudioIntent.ConfirmDeleteStory) },
                        onDismiss = { onIntent(StudioIntent.DismissDeleteDialog) },
                        inProgress = state.isDeleting,
                    )
                } else if (optionsTarget != null) {
                    StoryOptions(story = optionsTarget, onIntent = onIntent)
                }
            }
        }
    }

    if (state.report.isSheetOpen) {
        StoryReportSheet(
            state = state.report,
            onAction = { action -> onIntent(StudioIntent.Report(action)) },
        )
    }

    if (state.showResumeChoiceDialog) {
        ResumeChoiceDialog(
            onStartNew = { onIntent(StudioIntent.StartNewCreation) },
            onDismiss = { onIntent(StudioIntent.DismissResumeChoiceDialog) },
        )
    }
}

@Composable
private fun StoryOptions(
    story: StorySummary,
    onIntent: (StudioIntent) -> Unit,
) {
    ManyakOptionsDialogContent(preview = { MyStoryCardPreview(story = story) }) {
        ManyakOptionsDialogItem(
            iconRes = DesignsystemR.drawable.ic_info,
            label = stringResource(ReportR.string.story_report_action),
            onClick = { onIntent(StudioIntent.Report(StoryReportAction.Open)) },
        )
        ManyakOptionsDialogItem(
            iconRes = DesignsystemR.drawable.ic_delete,
            label = stringResource(R.string.studio_story_delete),
            onClick = { onIntent(StudioIntent.RequestDeleteStory) },
            isDanger = true,
        )
    }
}
