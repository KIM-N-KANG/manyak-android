package app.manyak.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.roundToInt
import app.manyak.designsystem.R as DesignsystemR

/**
 * 카드 옵션 시트를 여는 트리거. 카드 바탕 위에 놓이므로 필드를 깔지 않고 아이콘만 둔다.
 * 상자를 크게 잡을수록 제목 줄이 두꺼워져 제목이 아래로 밀리므로 아이콘에 바짝 붙인다.
 */
@Composable
fun ManyakMoreButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakIconButton(
        modifier = modifier,
        iconRes = DesignsystemR.drawable.ic_more_horizontal,
        contentDescription = contentDescription,
        onClick = onClick,
        size = MoreButtonSize,
        iconSize = ManyakTheme.sizes.iconSmall,
        shape = ManyakTheme.shapes.menuItem,
        tint = ManyakTheme.colors.textSubtle,
    )
}

/**
 * 제목 줄 옆에 놓인 [ManyakMoreButton] 의 정렬선. 아이콘 가운데를 제목 **첫 줄 글자**의 가운데에 맞춘다 —
 * 글줄 상자 가운데에 맞추면 위아래 여백까지 세는 탓에 아이콘이 글자보다 낮게 보인다. 제목 쪽은
 * `alignBy(FirstBaseline)` 이어야 한다.
 */
@Composable
fun RowScope.moreButtonTitleAlignment(titleStyle: TextStyle): Modifier {
    // 한글은 베이스라인 위로만 글자를 채우므로, 그 절반만큼 위가 글자의 가운데다.
    val halfGlyphHeight =
        with(LocalDensity.current) { (titleStyle.fontSize.toPx() * HANGUL_GLYPH_HEIGHT_RATIO / 2f).roundToInt() }
    return Modifier.alignBy { measured -> measured.measuredHeight / 2 + halfGlyphHeight }
}

private val MoreButtonSize = 24.dp

/**
 * 한글 글자의 가운데가 베이스라인 위 어디쯤인지의 배율. 글자는 베이스라인 위로 약 0.9em 을 채우므로
 * 그 가운데는 0.9em 의 절반 지점이다. 기기 캡처에서 글자와 아이콘의 픽셀 범위를 재서 맞춘 값이고
 * (0.73 은 아이콘이 1~2dp 낮아 보였다), 글자 크기를 키워도 비율은 그대로라 dp 가 아니라 배율로 둔다.
 */
private const val HANGUL_GLYPH_HEIGHT_RATIO = 0.9f
