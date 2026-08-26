package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic — 컨트롤 크기.
 *
 * 토큰 정본에는 높이가 없어 이 레포가 소유하는 값이다(DESIGN.md). 일반 컨트롤은 안드로이드 최소
 * 터치 타깃과 같은 48dp, 밀도 높은 입력·칩은 40dp 로 구분한다.
 */
@Immutable
data class ManyakSizes(
    /** 48dp — 버튼·탭처럼 탭 가능한 일반 컨트롤의 높이 */
    val control: Dp,
    /** 40dp — 입력창·칩·셀렉트 앵커의 최소 높이 */
    val input: Dp,
    /** 20dp — 라벨 옆에 붙는 아이콘·제공자 로고 */
    val icon: Dp,
    /** 24dp — 하단 탭 아이콘. 라벨 옆이 아니라 위에 놓여 탭의 주된 시각 요소이므로 [icon]보다 크다 */
    val tabIcon: Dp,
    /** 24dp — 마냑 로고 락업의 높이. 폭은 원본 비율(89:32)로 따라간다 */
    val logo: Dp,
)

internal val ManyakDefaultSizes =
    ManyakSizes(
        control = 48.dp,
        input = 40.dp,
        icon = 20.dp,
        tabIcon = 24.dp,
        logo = 24.dp,
    )
