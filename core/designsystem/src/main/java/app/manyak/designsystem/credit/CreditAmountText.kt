package app.manyak.designsystem.credit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.R
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 이프 마크를 앞세운 이프 수치. 마크는 옆 글자의 크기를 따라가고, 장식이라 접근성 이름을 두지 않는다 —
 * 글자가 수치를 읽는다.
 *
 * @param amount 화면에 보일 수치 문구("1,200", "0 이프" 등). 형식은 호출부가 정한다.
 * @param fullAmount 체험이 남아 깎인 경우의 정가. 취소선을 긋고 [amount] 왼쪽에 둔다.
 * @param pending 정책·잔여를 아직 못 받아 자리표시 숫자를 보이는 중인지. 골격과 같은 맥박을 얹는다.
 */
@Composable
fun CreditAmountText(
    amount: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fullAmount: String? = null,
    pending: Boolean = false,
) {
    Row(
        modifier = modifier.alpha(creditAmountAlpha(pending)),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CreditMark(style = style)
        if (fullAmount != null) {
            Text(text = fullAmount, style = style.copy(textDecoration = TextDecoration.LineThrough), color = color)
        }
        Text(text = amount, style = style, color = color)
    }
}

/** 이프 마크 한 개. 원본이 래스터라 PNG 를 그대로 쓰고 글자 크기(sp)에 맞춰 줄인다. */
@Composable
private fun CreditMark(
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val size = with(LocalDensity.current) { style.fontSize.toDp() }
    Image(
        painter = painterResource(R.drawable.if_credit_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Preview(showBackground = true, name = "이프 수치 · 정가 취소선")
@Composable
private fun CreditAmountTextPreview() {
    ManyakTheme(darkTheme = false) {
        CreditAmountText(
            amount = "0 이프",
            fullAmount = "80",
            style = ManyakTheme.typography.bodySmall,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}
