package app.manyak.root

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.manyak.core.ui.R
import app.manyak.designsystem.R as DesignsystemR

/**
 * 목적지와 탭의 대응을 맺는 유일한 자리.
 *
 * `:core:ui` 는 라우트를 알 수 없고 `:feature:*` 끼리도 서로 모르므로, 둘을 다 아는 `:app` 이 맺는다.
 */
internal enum class MainTab(
    @param:DrawableRes val selectedIconRes: Int,
    @param:DrawableRes val unselectedIconRes: Int,
    @param:StringRes val nameRes: Int,
) {
    HOME(
        selectedIconRes = DesignsystemR.drawable.ic_nav_home_filled,
        unselectedIconRes = DesignsystemR.drawable.ic_nav_home_outline,
        nameRes = R.string.main_tab_home,
    ),
    CHAT(
        selectedIconRes = DesignsystemR.drawable.ic_nav_chat_filled,
        unselectedIconRes = DesignsystemR.drawable.ic_nav_chat_outline,
        nameRes = R.string.main_tab_chat,
    ),
    STUDIO(
        selectedIconRes = DesignsystemR.drawable.ic_nav_studio_filled,
        unselectedIconRes = DesignsystemR.drawable.ic_nav_studio_outline,
        nameRes = R.string.main_tab_studio,
    ),
    MY(
        selectedIconRes = DesignsystemR.drawable.ic_nav_my_filled,
        unselectedIconRes = DesignsystemR.drawable.ic_nav_my_outline,
        nameRes = R.string.main_tab_my,
    ),
}
