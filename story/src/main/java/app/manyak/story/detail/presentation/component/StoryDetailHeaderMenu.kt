package app.manyak.story.detail.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakOptionItem
import app.manyak.designsystem.component.ManyakOptionsSheet
import app.manyak.designsystem.component.ManyakOptionsSheetHeader
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR
import app.manyak.story.R as StoryR

/**
 * 헤더 오른쪽 더보기. 카드 옵션과 같은 시트를 열고, 신고는 누구에게나, 삭제는 내 스토리로 들어왔을 때만 항목이 있다.
 *
 * 열림 상태는 구성 변경에서 살아남는다 — 회전했다고 열어 둔 시트가 닫히면 무엇을 누르려던 중이었는지 사라진다.
 *
 * @param isOwner 내가 만든 스토리인지. 머리글의 종류 문구를 가르고 삭제 항목을 둔다.
 */
@Composable
internal fun StoryDetailHeaderMenu(
    title: String,
    isOwner: Boolean,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    tint: Color,
) {
    var open by rememberSaveable { mutableStateOf(false) }

    ManyakIconButton(
        iconRes = DesignsystemR.drawable.ic_more_horizontal,
        contentDescription = stringResource(StoryR.string.story_detail_options),
        onClick = { open = true },
        // 표지 위에서는 앱바 아이콘과 같은 색을 따라간다.
        tint = tint,
    )

    if (open) {
        ManyakOptionsSheet(
            onDismissRequest = { open = false },
            header = {
                ManyakOptionsSheetHeader(
                    kind =
                        stringResource(
                            if (isOwner) {
                                StoryR.string.story_detail_kind_mine
                            } else {
                                StoryR.string.story_detail_kind_story
                            },
                        ),
                    title = title,
                )
            },
        ) {
            // 신고 시트·삭제 확인은 이 시트와 별개의 모달이라 먼저 닫고 연다.
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_alert_triangle,
                label = stringResource(ReportR.string.story_report_action),
                onClick = {
                    open = false
                    onReport()
                },
            )
            if (isOwner) {
                ManyakOptionItem(
                    iconRes = DesignsystemR.drawable.ic_delete,
                    label = stringResource(CommonR.string.studio_story_delete),
                    onClick = {
                        open = false
                        onDelete()
                    },
                    isDestructive = true,
                )
            }
        }
    }
}
