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
    /** 2dp — 아이콘과 라벨 사이 */
    val hairline: Dp,
    /** 4dp — 인접한 인라인 요소 */
    val inline: Dp,
    /** 6dp — 촘촘한 요소 사이 */
    val dense: Dp,
    /** 8dp — 리스트 항목 간격·버튼 내부 세로 */
    val compact: Dp,
    /** 10dp — 입력·칩·메뉴 항목의 세로 패딩 */
    val controlVertical: Dp,
    /** 12dp — 컴포넌트 내부 기본 */
    val component: Dp,
    /** 14dp — 입력·칩·메뉴 항목의 가로 패딩 */
    val controlHorizontal: Dp,
    /** 16dp — 화면 좌우 여백 */
    val gutter: Dp,
    /** 20dp — 읽는 본문 블록의 세로 여백과 블록 안 조각 사이 */
    val passage: Dp,
    /** 24dp — 섹션 사이 */
    val section: Dp,
    /** 32dp — 큰 구획 사이 */
    val block: Dp,
    /** 64dp — 화면 상단 첫 요소 위 여유 */
    val screenTop: Dp,
    /** 40dp — 스크롤 영역 하단 여유 */
    val screenBottom: Dp,
)

internal val ManyakDefaultSpacing =
    ManyakSpacing(
        hairline = 2.dp,
        inline = 4.dp,
        dense = 6.dp,
        compact = 8.dp,
        controlVertical = 10.dp,
        component = 12.dp,
        controlHorizontal = 14.dp,
        gutter = 16.dp,
        passage = 20.dp,
        section = 24.dp,
        block = 32.dp,
        screenTop = 64.dp,
        screenBottom = 32.dp,
    )
