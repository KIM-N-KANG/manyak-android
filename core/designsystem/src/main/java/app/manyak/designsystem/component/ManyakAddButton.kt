package app.manyak.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

/**
 * 목록 끝에 항목을 하나 더하는 보조 버튼. 제작 입력의 인물과 상황 추가, 마이의 페르소나 추가가 함께 쓴다.
 */
@Composable
fun ManyakAddButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ManyakTheme.shapes.control,
    @DrawableRes iconRes: Int = DesignsystemR.drawable.ic_add,
) {
    val contentColor = if (enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled
    Row(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(shape)
                .background(ManyakTheme.colors.backgroundNeutral)
                .border(1.dp, ManyakTheme.colors.border, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = label,
            style = ManyakTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
