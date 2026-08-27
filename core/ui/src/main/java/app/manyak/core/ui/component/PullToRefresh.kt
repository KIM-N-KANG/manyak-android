package app.manyak.core.ui.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 목록을 당겨서 새로고침하는 컨테이너. [content] 에는 스크롤되는 목록을 둔다 — 당김은 목록의
 * 스크롤에서 나오므로 스크롤되지 않는 콘텐츠에는 이 컨테이너를 씌워도 아무 일도 일어나지 않는다.
 *
 * 표시자는 셸이 넘긴 [contentPadding] 의 상단만큼 내려 둔다. 목록은 헤더 아래로 흘러 들어가도
 * 되지만 표시자는 그 자리에서 헤더에 완전히 가려, 다 당겨도 보이지 않는다.
 */
@Composable
fun ManyakPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
                containerColor = ManyakTheme.colors.surfaceRaised,
                color = ManyakTheme.colors.progressIndicator,
            )
        },
        content = content,
    )
}
