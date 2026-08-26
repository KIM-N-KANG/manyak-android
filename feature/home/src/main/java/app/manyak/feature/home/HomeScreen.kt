package app.manyak.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 홈 탭(스토리 목록). 헤더와 하단 탭은 셸이 그리므로 여기서는 콘텐츠만 둔다.
 *
 * 아직 목록이 없어 비어 있다. [contentPadding] 은 셸의 chrome 이 차지한 만큼이므로, 스크롤 목록을
 * 넣을 때는 `Modifier.padding` 이 아니라 목록의 `contentPadding` 으로 넘겨야 콘텐츠가 헤더 아래로
 * 흘러 들어간다.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    )
}
