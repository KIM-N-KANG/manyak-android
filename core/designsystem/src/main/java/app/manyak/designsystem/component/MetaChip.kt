package app.manyak.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 아이콘 하나에 값 하나를 붙인 메타 표시. 채팅 목록 카드와 내 스토리 카드가 함께 쓴다.
 *
 * 아이콘에는 이름을 붙이지 않고, 숫자·시각만 읽히면 무엇인지 알 수 없으므로 칩 전체를 한 문장으로
 * 읽힌다. 그래서 [description] 은 값이 아니라 "턴 15회" 처럼 무엇인지까지 담은 문장이어야 한다.
 */
@Composable
fun MetaChip(
    @DrawableRes iconRes: Int,
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(if (compact) CompactMetaIconSize else MetaIconSize),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ManyakTheme.colors.textSubtle,
        )
        Text(
            text = text,
            style = if (compact) ManyakTheme.typography.bodySmall else ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 메타 글줄 옆에 붙는 아이콘이라 토큰의 가장 작은 아이콘보다도 작다. 웹도 같은 14px 다. */
private val MetaIconSize = 14.dp

/** 한 단계 작은 카드 미리보기용. 글자가 12sp 로 줄어드는 만큼 아이콘도 따라 줄인다. */
private val CompactMetaIconSize = 12.dp
