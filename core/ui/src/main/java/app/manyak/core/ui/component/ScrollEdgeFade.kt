package app.manyak.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 스크롤 콘텐츠가 잘리는 아래 경계를 부드럽게 만드는 띠.
 *
 * **콘텐츠 위에 겹쳐 놓는다**(`Box` 안에서 아래쪽 정렬) — 스크롤 영역의 형제로 두면 자리만 차지하고
 * 가릴 것이 없어 아무것도 보이지 않는다.
 *
 * 늘 떠 있는 chrome(하단 탭 바)에는 쓰지 않는다. 그쪽은 스크롤 여부와 무관하게 경계가 상시
 * 필요해 경계선이 맡는다([ManyakNavigationBar]).
 */
@Composable
fun ScrollEdgeFade(modifier: Modifier = Modifier) {
    val surface = ManyakTheme.colors.surface
    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ScrollEdgeFadeHeight)
                .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
    )
}

/** 페이드 띠의 높이. 겹치는 쪽이 아래 여백을 맞춰야 할 때 읽는다. */
val ScrollEdgeFadeHeight: Dp = 16.dp
