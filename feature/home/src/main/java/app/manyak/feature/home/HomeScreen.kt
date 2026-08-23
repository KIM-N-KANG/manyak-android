package app.manyak.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 홈 탭(스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * 아직 목록이 없어 콘텐츠는 비어 있다. [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로, 스크롤
 * 목록을 넣을 때는 `Modifier.padding` 이 아니라 목록의 `contentPadding` 으로 넘겨야 콘텐츠가 헤더
 * 아래로 흘러 들어간다.
 *
 * 제작 퍼널 진입 FAB 은 셸이 아니라 이 화면이 소유한다 — 다른 탭에서는 표시하지 않는 홈의 주 동작이다.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onCreateStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        CreateStoryFab(
            onClick = onCreateStory,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ManyakTheme.spacing.gutter),
        )
    }
}

/**
 * 간편 제작 진입 버튼. 주 동작 색을 쓰고, 이 디자인 시스템은 그림자를 쓰지 않으므로 고도를 없앤다.
 */
@Composable
private fun CreateStoryFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        shape = ManyakTheme.shapes.card,
        containerColor = ManyakTheme.colors.backgroundBrandBold,
        contentColor = ManyakTheme.colors.textInverse,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.home_create_story),
        )
    }
}

@Preview(showBackground = true, name = "홈 · 라이트")
@Composable
private fun HomeScreenPreview() {
    ManyakTheme(darkTheme = false) {
        HomeScreen(contentPadding = PaddingValues(0.dp), onCreateStory = {})
    }
}
