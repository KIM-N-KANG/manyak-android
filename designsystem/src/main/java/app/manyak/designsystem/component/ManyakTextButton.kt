package app.manyak.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 앱의 텍스트 버튼. M3 `TextButton` 은 기본 모양이 완전한 알약이라 눌림 리플이 채움 버튼과 다른
 * 모양으로 돈다. 기본은 채움 버튼과 같은 컨트롤 곡률이고, 짝이 되는 채움 버튼이 다른 곡률이면 [shape] 로
 * 맞춘다. 글자 스타일·색은 호출부가 정한다.
 */
@Composable
fun ManyakTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ManyakTheme.shapes.control,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        content = content,
    )
}
