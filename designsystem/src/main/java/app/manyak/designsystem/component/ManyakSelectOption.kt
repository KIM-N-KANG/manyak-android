package app.manyak.designsystem.component

import androidx.annotation.DrawableRes

/** 셀렉트 항목 하나. [value] 는 화면이 쓰는 값이고 [label] 은 사람이 읽는 문구다. */
data class ManyakSelectOption<T>(
    val value: T,
    val label: String,
)

/**
 * 셀렉트 메뉴 맨 아래에 붙는 동작 항목. 고를 수 있는 값이 아니라 눌러도 선택이 바뀌지 않고, 메뉴를 닫은 뒤
 * [onClick] 을 부른다. 아이콘이 왼쪽에 붙어 값 항목과 구분된다.
 */
data class ManyakSelectAction(
    @param:DrawableRes val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
)
