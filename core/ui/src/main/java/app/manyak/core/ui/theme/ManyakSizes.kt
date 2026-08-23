package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic — 컨트롤 크기.
 *
 * 토큰 정본에는 높이가 없어 이 레포가 소유하는 값이다(DESIGN.md). [control]을 안드로이드 최소 터치
 * 타깃과 같은 48dp 로 두어 **보이는 크기와 눌리는 크기가 어긋나지 않게** 한다 — Material3 기본값
 * (40dp)은 터치 영역만 48dp 로 넓혀 두 크기가 다르다.
 */
@Immutable
data class ManyakSizes(
    /** 버튼·입력창·탭처럼 탭 가능한 컨트롤의 높이 */
    val control: Dp,
    /** 라벨 옆에 붙는 아이콘·제공자 로고 */
    val icon: Dp,
    /** 하단 탭 아이콘. 라벨 옆이 아니라 위에 놓여 탭의 주된 시각 요소이므로 [icon]보다 크다 */
    val tabIcon: Dp,
    /** 마냑 로고 락업의 높이. 폭은 원본 비율(89:32)로 따라간다 */
    val logo: Dp,
)

internal val ManyakDefaultSizes =
    ManyakSizes(
        control = 48.dp,
        icon = 20.dp,
        tabIcon = 24.dp,
        logo = 24.dp,
    )
