package app.manyak.notice.presentation

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
import app.manyak.notice.R as NoticeR

/**
 * 홈·채팅·제작 헤더 오른쪽의 공지사항 버튼. 마이의 알림 설정과 같은 종 아이콘이고, 안 읽은 공지가 있으면
 * 아이콘 오른쪽 위에 빨간 점을 찍고 접근 이름을 바꾼다. 점은 색만으로 말하지 않도록 이름이 함께 바뀐다.
 */
@Composable
fun NoticeHeaderButton(
    hasUnread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        ManyakIconButton(
            iconRes = DesignsystemR.drawable.ic_bell,
            contentDescription =
                stringResource(
                    if (hasUnread) NoticeR.string.notice_header_button_unread else NoticeR.string.notice_title,
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
