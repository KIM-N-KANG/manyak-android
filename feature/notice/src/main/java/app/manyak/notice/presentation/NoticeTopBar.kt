package app.manyak.notice.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.notice.R as NoticeR

/** 목록·상세가 같이 쓰는 "공지사항" 뒤로가기 헤더. 셸 밖 전체 화면이라 화면이 직접 갖는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(NoticeR.string.notice_title),
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
        },
        navigationIcon = {
            ManyakIconButton(
                iconRes = DesignsystemR.drawable.ic_arrow_back,
                contentDescription = stringResource(CommonR.string.common_back),
                onClick = onBack,
            )
        },
        // 화면 루트에서 적용한 safeDrawing 인셋이 중복되지 않게 한다.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}
