package app.manyak.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic — 여백. 이름은 크기가 아니라 상황으로 붙인다.
 * 값이 바뀌어도 [gutter]는 그대로 유효하지만 `space.200`은 의미를 잃기 때문이다.
 */
@Immutable
data class ManyakSpacing(
    /** 아이콘과 라벨 사이 */
    val hairline: Dp,
    /** 인접한 인라인 요소 */
    val inline: Dp,
    /** 리스트 항목 간격·버튼 내부 세로 */
    val compact: Dp,
    /** 컴포넌트 내부 기본 */
    val component: Dp,
    /** 화면 좌우 여백 */
    val gutter: Dp,
    /** 섹션 사이 */
    val section: Dp,
    /** 큰 구획 사이 */
    val block: Dp,
    /** 화면 상단 첫 요소 위 여유 */
    val screenTop: Dp,
    /** 스크롤 영역 하단 여유 */
    val screenBottom: Dp,
)

internal val ManyakDefaultSpacing =
    ManyakSpacing(
        hairline = 2.dp,
        inline = 4.dp,
        compact = 8.dp,
        component = 12.dp,
        gutter = 16.dp,
        section = 24.dp,
        block = 32.dp,
        screenTop = 64.dp,
        screenBottom = 40.dp,
    )
