package app.manyak.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic — 모서리. dp가 아니라 Shape로 내린다.
 * `radius.pill`은 큰 dp가 아니라 [CircleShape]이므로 값으로 두면 잘못 쓰이기 쉽다.
 */
@Immutable
data class ManyakShapes(
    /** 6dp — 체크박스처럼 한 변이 20dp 남짓인 작은 네모 */
    val checkbox: CornerBasedShape,
    /** 10dp — 셀렉트 메뉴 항목·라벨 없는 아이콘 버튼 */
    val menuItem: CornerBasedShape,
    /** 12dp — 썸네일·작은 아이콘 컨테이너 */
    val thumbnail: CornerBasedShape,
    /** 14dp — 버튼·입력창·탭 */
    val control: CornerBasedShape,
    /** 16dp — 카드·리스트 항목 */
    val card: CornerBasedShape,
    /** 20dp — 다이얼로그 */
    val overlay: CornerBasedShape,
    /** 20dp — 바텀시트. 아래쪽은 화면 끝에 붙으므로 위쪽 두 모서리만 깎는다 */
    val sheet: CornerBasedShape,
    /** CircleShape — 배지·칩·아바타 */
    val pill: CornerBasedShape,
)

internal val ManyakDefaultShapes =
    ManyakShapes(
        checkbox = RoundedCornerShape(6.dp),
        menuItem = RoundedCornerShape(10.dp),
        thumbnail = RoundedCornerShape(12.dp),
        control = RoundedCornerShape(14.dp),
        card = RoundedCornerShape(16.dp),
        overlay = RoundedCornerShape(20.dp),
        sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        pill = CircleShape,
    )

/**
 * 테두리 안쪽에 놓을 모양. 반지름을 선 두께만큼 줄여 **선과 동심원**이 되게 한다.
 *
 * 반지름을 그대로 두면 안쪽 도형의 곡률이 선보다 급해 곡률 구간에서만 내용이 선 가까이 붙고,
 * 내용이 선보다 어두우면 그 겹침이 곡률에서만 진한 띠로 보인다. CSS 가 `border-radius` 에 적용하는
 * 규칙과 같다.
 *
 * 모서리가 없는 모양은 곡률이 없어 그대로 돌려준다.
 */
fun Shape.insetForBorder(width: Dp): Shape {
    if (this !is CornerBasedShape) return this
    return copy(
        topStart = InsetCornerSize(topStart, width),
        topEnd = InsetCornerSize(topEnd, width),
        bottomEnd = InsetCornerSize(bottomEnd, width),
        bottomStart = InsetCornerSize(bottomStart, width),
    )
}

/** 원본 모서리에서 [width] 만큼 줄인 크기. 각진 모서리 아래로는 내려가지 않는다. */
@Immutable
private data class InsetCornerSize(
    private val source: CornerSize,
    private val width: Dp,
) : CornerSize {
    override fun toPx(
        shapeSize: Size,
        density: Density,
    ): Float = (source.toPx(shapeSize, density) - with(density) { width.toPx() }).coerceAtLeast(0f)
}
