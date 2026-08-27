package app.manyak.core.ui.component

/** 셀렉트 항목 하나. [value] 는 화면이 쓰는 값이고 [label] 은 사람이 읽는 문구다. */
data class ManyakSelectOption<T>(
    val value: T,
    val label: String,
)
