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
)

internal val ManyakDefaultMotion =
    ManyakMotion(
        screenTransitionMillis = 150,
    )
