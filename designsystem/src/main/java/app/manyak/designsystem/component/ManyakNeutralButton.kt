package app.manyak.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 보조 동작 버튼 — 퍼널의 이전·다시 만들기, 조회 실패의 다시 시도. 주 동작 색을 쓰지 않는 이유는
 * 화면이 실패로 멈춘 자리에서 브랜드 색 버튼이 뜨면 무언가를 새로 시작하라는 권유처럼 읽히기 때문이다.
 */
@Composable
fun ManyakNeutralButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        enabled = enabled,
        onClick = onClick,
        shape = ManyakTheme.shapes.control,
        border = BorderStroke(NeutralBorderWidth, ManyakTheme.colors.border),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.backgroundNeutral,
                contentColor = ManyakTheme.colors.text,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        Text(text = label, style = ManyakTheme.typography.labelLarge)
    }
}

private val NeutralBorderWidth = 1.dp

@Preview(showBackground = true, name = "보조 버튼")
@Composable
private fun ManyakNeutralButtonPreview() {
    ManyakTheme(darkTheme = false) {
        ManyakNeutralButton(label = "다시 시도", onClick = {})
    }
}
