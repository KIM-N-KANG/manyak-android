package app.manyak.home.search.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/** 탐색 헤더의 검색 버튼. 셸이 알림 버튼 왼쪽에 둔다. */
@Composable
fun StorySearchHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManyakIconButton(
        modifier = modifier,
        iconRes = DesignsystemR.drawable.ic_search,
        contentDescription = stringResource(HomeR.string.search_label),
        onClick = onClick,
    )
}
