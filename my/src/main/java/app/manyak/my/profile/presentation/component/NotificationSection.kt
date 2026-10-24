package app.manyak.my.profile.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.my.R as MyR

/** 알림 설정 아래 공지사항. 안 읽은 공지가 있으면 라벨 옆 "새 공지" 배지가 헤더의 점과 같이 켜진다. */
@Composable
internal fun NotificationSection(
    hasUnreadNotice: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MySection(labelRes = MyR.string.my_section_notification, modifier = modifier) {
        MyMenuItem(
            iconRes = DesignsystemR.drawable.ic_bell,
            labelRes = MyR.string.my_notification_settings,
            onClick = onOpenNotificationSettings,
            trailing = { MenuTrailingIcon(iconRes = DesignsystemR.drawable.ic_chevron_right) },
        )
        MyMenuItem(
            iconRes = DesignsystemR.drawable.ic_megaphone,
            labelRes = MyR.string.my_notices,
            onClick = onOpenNotices,
            labelBadge = if (hasUnreadNotice) stringResource(MyR.string.my_notice_new_badge) else null,
            trailing = { MenuTrailingIcon(iconRes = DesignsystemR.drawable.ic_chevron_right) },
        )
    }
}
