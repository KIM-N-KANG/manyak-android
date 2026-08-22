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
    /** 썸네일·작은 아이콘 컨테이너 */
    val thumbnail: CornerBasedShape,
    /** 버튼·입력창·탭 */
    val control: CornerBasedShape,
    /** 카드·리스트 항목 */
    val card: CornerBasedShape,
    /** 바텀시트·다이얼로그 */
    val overlay: CornerBasedShape,
    /** 배지·칩·아바타 */
    val pill: CornerBasedShape,
)

internal val ManyakDefaultShapes =
    ManyakShapes(
        thumbnail = RoundedCornerShape(12.dp),
        control = RoundedCornerShape(14.dp),
        card = RoundedCornerShape(16.dp),
        overlay = RoundedCornerShape(20.dp),
        pill = CircleShape,
    )
