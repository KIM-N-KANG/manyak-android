package app.manyak.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.R
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 동의·확인 항목의 체크 표시. **누르는 대상이 아니다** — 줄 전체가 토글을 맡아 문구를 눌러도 켜지고,
 * 최소 터치 타깃도 그 줄이 확보한다. 그래서 여기에는 클릭도 접근성 이름도 붙이지 않는다.
 */
@Composable
fun ManyakCheckbox(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderWidth = if (isChecked) 1.dp else ManyakTheme.sizes.selectionBorderWidth
    val borderColor = if (isChecked) ManyakTheme.colors.brand else ManyakTheme.colors.border
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.icon)
                .clip(ManyakTheme.shapes.checkbox)
                .background(if (isChecked) ManyakTheme.colors.brand else ManyakTheme.colors.surface)
                .border(borderWidth, borderColor, ManyakTheme.shapes.checkbox),
        contentAlignment = Alignment.Center,
    ) {
        if (isChecked) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                tint = ManyakTheme.colors.textInverse,
            )
        }
    }
}
