package app.manyak.notification.inbox.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.notification.R as NotificationR

@Composable
fun NotificationHeaderButton(
    hasUnread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        ManyakIconButton(
            iconRes = DesignsystemR.drawable.ic_bell,
            contentDescription =
                stringResource(
                    if (hasUnread) NotificationR.string.inbox_header_unread else NotificationR.string.inbox_title,
                ),
            onClick = onClick,
        )
        if (hasUnread) {
            // 바탕색 테두리로 아이콘 선과 점을 떼어 놓는다.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(ManyakTheme.spacing.compact)
                        .size(ManyakTheme.sizes.unreadDot + ManyakTheme.spacing.hairline * 2)
                        .border(ManyakTheme.spacing.hairline, ManyakTheme.colors.surface, ManyakTheme.shapes.pill)
                        .padding(ManyakTheme.spacing.hairline)
                        .background(ManyakTheme.colors.backgroundDangerBold, ManyakTheme.shapes.pill),
            )
        }
    }
}
