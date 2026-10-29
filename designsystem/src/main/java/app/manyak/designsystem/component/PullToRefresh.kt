package app.manyak.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.delay

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
    // 응답이 빠르면 표시자가 돌기 전에 사라져 새로고침됐는지 알 수 없다. 당긴 뒤 최소한은 돌게 둔다.
    var holdsIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(holdsIndicator) {
        if (holdsIndicator) {
            delay(MIN_REFRESH_INDICATOR_MILLIS)
            holdsIndicator = false
        }
    }
    val showsRefreshing = isRefreshing || holdsIndicator
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pullToRefresh(
                    isRefreshing = showsRefreshing,
                    state = state,
                    threshold = RefreshThreshold,
                    onRefresh = {
                        holdsIndicator = true
                        onRefresh()
                    },
                ),
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = showsRefreshing,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding()),
            containerColor = ManyakTheme.colors.surfaceRaised,
            color = ManyakTheme.colors.progressIndicator,
        )
    }
}

// M3 는 손가락 이동의 절반만 당김으로 세므로 112dp 를 끌면 발동한다. 기본값 80dp 는 160dp 를
// 끌어야 해서 너무 길다. 표시자가 멈추는 높이는 기본값 80dp 그대로 둔다.
private val RefreshThreshold = 56.dp

private const val MIN_REFRESH_INDICATOR_MILLIS = 800L
