package app.manyak.studio.presentation.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakDestructiveDialogContent
import app.manyak.designsystem.component.ManyakDialog
import app.manyak.designsystem.component.ManyakOptionsDialogContent
import app.manyak.designsystem.component.ManyakOptionsDialogItem
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.report.presentation.StoryReportAction
import app.manyak.report.presentation.component.StoryReportSheet
import app.manyak.studio.presentation.StudioCard
import app.manyak.studio.presentation.StudioIntent
import app.manyak.studio.presentation.StudioUiState
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR
import app.manyak.studio.R as StudioR

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
                onIntent(if (deleteTarget != null) StudioIntent.DismissDeleteDialog else StudioIntent.CloseCardOptions)
            },
        ) {
            Crossfade(
                targetState = deleteTarget,
                animationSpec = tween(ManyakTheme.motion.elementEnterMillis),
                label = "storyCardDialog",
            ) { target ->
                if (target != null) {
                    // 로컬 초안·요청은 서버 스토리가 아니라 잃는 것이 다르므로 문구를 나눈다.
                    val isStory = target is StudioCard.Story
                    ManyakDestructiveDialogContent(
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
                    )
                } else if (optionsTarget != null) {
                    CardOptions(card = optionsTarget, onIntent = onIntent)
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

/** 스토리 카드는 신고·삭제, 초안·실패 요청 카드는 삭제만 있다. 완성 중 카드는 옵션 자체가 없다. */
@Composable
private fun CardOptions(
    card: StudioCard,
    onIntent: (StudioIntent) -> Unit,
) {
    ManyakOptionsDialogContent(
        preview = {
            when (card) {
                is StudioCard.Story -> MyStoryCardPreview(story = card.story)
                StudioCard.Draft -> CreationProgressCardPreview(kind = CreationProgressCardKind.Draft)
                is StudioCard.FailedRequest -> CreationProgressCardPreview(kind = CreationProgressCardKind.Failed)
            }
        },
    ) {
        if (card is StudioCard.Story) {
            ManyakOptionsDialogItem(
                iconRes = DesignsystemR.drawable.ic_info,
                label = stringResource(ReportR.string.story_report_action),
                onClick = { onIntent(StudioIntent.Report(StoryReportAction.Open)) },
            )
        }
        ManyakOptionsDialogItem(
            iconRes = DesignsystemR.drawable.ic_delete,
            label = stringResource(CommonR.string.studio_story_delete),
            onClick = { onIntent(StudioIntent.RequestDelete) },
            isDanger = true,
        )
    }
}
