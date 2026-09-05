package app.manyak.feature.story

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakOptionsMenu
import app.manyak.designsystem.component.ManyakOptionsMenuItem
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR

/** 헤더 오른쪽 더보기 메뉴. 신고는 누구에게나, 삭제는 내 스토리로 들어왔을 때만 항목이 있다. */
@Composable
internal fun StoryDetailHeaderMenu(
    onReport: () -> Unit,
    onDelete: (() -> Unit)?,
    tint: Color,
) {
    ManyakOptionsMenu(
        contentDescription = stringResource(R.string.story_detail_options),
        // 표지 위에서는 앱바 아이콘과 같은 색을 따라간다.
        tint = tint,
    ) { dismiss ->
        ManyakOptionsMenuItem(
            iconRes = DesignsystemR.drawable.ic_info,
            label = stringResource(ReportR.string.story_report_action),
            onClick = {
                dismiss()
                onReport()
            },
        )
        if (onDelete != null) {
            ManyakOptionsMenuItem(
                iconRes = DesignsystemR.drawable.ic_delete,
                label = stringResource(R.string.studio_story_delete),
                onClick = {
                    dismiss()
                    onDelete()
                },
                isDanger = true,
            )
        }
    }
}
