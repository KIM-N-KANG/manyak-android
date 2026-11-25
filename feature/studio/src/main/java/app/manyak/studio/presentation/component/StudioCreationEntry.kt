package app.manyak.studio.presentation.component

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.studio.R as StudioR

/**
 * 제작 퍼널 진입 FAB. 목록 맨 위에서는 웹처럼 라벨을 펼치고, 스크롤을 내리면 아이콘만 남긴다 —
 * 펼침·접힘 전환은 M3 확장 FAB 이 맡는다.
 */
@Composable
internal fun CreateStoryFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        expanded = expanded,
        shape = ManyakTheme.shapes.pill,
        containerColor = ManyakTheme.colors.brand,
        contentColor = ManyakTheme.colors.textInverse,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
        icon = {
            Icon(
                painter = painterResource(DesignsystemR.drawable.ic_add),
                contentDescription = stringResource(StudioR.string.studio_create_story),
            )
        },
        text = {
            Text(
                text = stringResource(StudioR.string.studio_create_story_short),
                style = ManyakTheme.typography.labelLarge,
            )
        },
    )
}
