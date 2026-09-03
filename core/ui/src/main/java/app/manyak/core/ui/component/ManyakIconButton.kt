package app.manyak.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.theme.ManyakShapes
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 라벨 없는 아이콘 버튼. 눌림 리플의 모양을 [shape] 가 정한다 — 앱바의 뒤로가기·닫기는 안드로이드
 * 관례대로 원([ManyakShapes.pill])이고, 입력 칸 옆·카드 제목 줄처럼 콘텐츠 안에 놓이는 작은 버튼은
 * 메뉴 항목과 같은 [ManyakShapes.menuItem] 이다. M3 `IconButton` 은 모양을 고를 수 없어 쓰지 않는다.
 *
 * @param size 터치 영역 한 변. 기본은 최소 터치 타깃이고, 밀집한 자리는 [ManyakTheme.sizes.controlSmall] 을 쓴다.
 * @param iconSize 아이콘 한 변. 기본은 앱바 아이콘과 같은 24dp 다.
 */
@Composable
fun ManyakIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ManyakTheme.sizes.control,
    iconSize: Dp = AppBarIconSize,
    shape: Shape = ManyakTheme.shapes.pill,
    tint: Color = ManyakTheme.colors.text,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(iconSize),
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) tint else ManyakTheme.colors.textDisabled,
        )
    }
}

/** M3 앱바가 그리는 아이콘과 같은 크기. 뒤로가기·닫기가 다른 앱바와 같은 무게로 보이게 한다. */
private val AppBarIconSize = 24.dp
