package app.manyak.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Semantic — 모서리. dp가 아니라 Shape로 내린다.
 * `radius.pill`은 큰 dp가 아니라 [CircleShape]이므로 값으로 두면 잘못 쓰이기 쉽다.
 */
@Immutable
data class ManyakShapes(
    /** 10dp — 셀렉트 메뉴 항목 */
    val menuItem: CornerBasedShape,
    /** 12dp — 썸네일·작은 아이콘 컨테이너 */
    val thumbnail: CornerBasedShape,
    /** 14dp — 버튼·입력창·탭 */
    val control: CornerBasedShape,
    /** 16dp — 카드·리스트 항목 */
    val card: CornerBasedShape,
    /** 20dp — 바텀시트·다이얼로그 */
    val overlay: CornerBasedShape,
    /** CircleShape — 배지·칩·아바타 */
    val pill: CornerBasedShape,
)

internal val ManyakDefaultShapes =
    ManyakShapes(
        menuItem = RoundedCornerShape(10.dp),
        thumbnail = RoundedCornerShape(12.dp),
        control = RoundedCornerShape(14.dp),
        card = RoundedCornerShape(16.dp),
        overlay = RoundedCornerShape(20.dp),
        pill = CircleShape,
    )
