package app.manyak.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 옵션 시트의 항목 버튼 하나. 카드 옵션·채팅 메뉴·상세 옵션이 같은 줄을 쓴다.
 *
 * [inProgress] 동안은 오른쪽에 스피너를 두고 탭을 막는다 — 새 채팅 시작처럼 결과가 화면 이동인 항목은
 * 연타로 두 번 만들어지면 안 된다. 라벨·크기는 그대로 두어 줄이 줄어들지 않는다.
 */
@Composable
fun ManyakOptionItem(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    inProgress: Boolean = false,
) {
    val color = if (isDestructive) ManyakTheme.colors.textDanger else ManyakTheme.colors.text
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(enabled = !inProgress, role = Role.Button, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
        )
        Text(modifier = Modifier.weight(1f), text = label, style = ManyakTheme.typography.bodyLarge, color = color)
        if (inProgress) {
            ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon))
        }
    }
}
