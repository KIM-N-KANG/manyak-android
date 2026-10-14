package app.manyak.chat.room.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 안내 카드와 실패 안내 아래에 놓이는 작은 버튼. 웹의 작은 버튼과 같은 높이, 여백, 글자를 쓴다.
 * M3 `Button` 은 최소 높이 40dp 와 최소 폭이 붙어 이 크기로 줄일 수 없어 직접 그린다.
 * 기본은 주 동작 색이고, 테두리형과 글자형은 호출부가 색을 바꿔 쓴다.
 */
@Composable
internal fun ChatSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ManyakTheme.colors.brand,
    contentColor: Color = ManyakTheme.colors.textInverse,
    borderColor: Color = Color.Transparent,
) {
    val shape = ManyakTheme.shapes.menuItem
    Box(
        modifier =
            modifier
                // 큰 글자에서는 글줄이 높이를 넘기므로 최소값으로만 잡는다.
                .heightIn(min = ManyakTheme.sizes.controlSmall)
                .clip(shape)
                .background(containerColor)
                .border(SmallButtonBorderWidth, borderColor, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.component),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = ManyakTheme.typography.labelSmall, color = contentColor)
    }
}

private val SmallButtonBorderWidth = 1.dp
