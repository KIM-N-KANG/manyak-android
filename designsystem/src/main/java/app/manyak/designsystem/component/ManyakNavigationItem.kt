package app.manyak.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * 하단 내비게이션의 항목 하나. 목적지를 모르고 그릴 것과 눌렸을 때 할 일만 안다.
 *
 * 아이콘을 선택·비선택 두 벌로 받는 이유는 선택 여부를 모양과 색 둘로 말하기 위해서다 — 색 하나로만
 * 말하면 색각 이상이나 낮은 명암 환경에서 구분되지 않는다.
 */
@Immutable
data class ManyakNavigationItem(
    @param:DrawableRes val selectedIconRes: Int,
    @param:DrawableRes val unselectedIconRes: Int,
    @param:StringRes val nameRes: Int,
    val selected: Boolean,
    val onSelect: () -> Unit,
)
