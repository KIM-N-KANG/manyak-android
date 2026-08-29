package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable

/**
 * Semantic — 전환 시간.
 *
 * 토큰 정본에 모션이 없어 이 레포가 소유하는 값이다(DESIGN.md). 시간만 정하고 어떤 속성을
 * 움직일지는 쓰는 쪽이 정한다 — 화면 전환은 페이드, 다른 곳은 다른 방식일 수 있다.
 */
@Immutable
data class ManyakMotion(
    /** 화면·탭이 바뀔 때의 교차 페이드 길이(밀리초) */
    val screenTransitionMillis: Int,
    /** 화면 안의 작은 요소가 나타날 때의 길이(밀리초) */
    val elementEnterMillis: Int,
    /** 화면 안의 작은 요소가 사라질 때의 길이(밀리초) */
    val elementExitMillis: Int,
    /** 목록 항목이 차례로 나타날 때 항목 하나의 길이(밀리초) */
    val listItemEnterMillis: Int,
    /** 목록 항목 사이의 시작 간격(밀리초) */
    val listItemStaggerMillis: Int,
)

internal val ManyakDefaultMotion =
    ManyakMotion(
        screenTransitionMillis = 150,
        elementEnterMillis = 200,
        elementExitMillis = 150,
        listItemEnterMillis = 300,
        listItemStaggerMillis = 80,
    )
