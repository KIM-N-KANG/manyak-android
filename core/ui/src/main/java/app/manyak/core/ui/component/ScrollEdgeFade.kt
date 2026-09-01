package app.manyak.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * **알파를 선형으로 올리지 않는다.** 두 스톱짜리 선형 램프는 투명한 쪽 끝에서 띠 경계가 눈에 띈다 —
 * 인지되는 불투명도가 알파에 선형이 아니기 때문이고, 명암이 강한 이미지 위에서 특히 드러난다.
 * 투명한 쪽을 길게 끌고 불투명한 쪽에서 빠르게 채우는 스톱을 둬 경계를 없앤다.
 *
 * 늘 떠 있는 chrome(하단 탭 바)에는 쓰지 않는다. 그쪽은 스크롤 여부와 무관하게 경계가 상시
 * 필요해 경계선이 맡는다([ManyakNavigationBar]).
 */
@Composable
fun ScrollEdgeFade(modifier: Modifier = Modifier) {
    val surface = ManyakTheme.colors.surface
    val brush =
        remember(surface) {
            val stops: Array<Pair<Float, Color>> =
                FadeStops.map { (position, alpha) -> position to surface.copy(alpha = alpha) }.toTypedArray()
            Brush.verticalGradient(colorStops = stops)
        }
    Spacer(modifier = modifier.fillMaxWidth().height(ScrollEdgeFadeHeight).background(brush))
}

/** 페이드 띠의 높이. 겹치는 쪽이 아래 여백을 맞춰야 할 때 읽는다. */
val ScrollEdgeFadeHeight: Dp = 40.dp

/** 위치별 알파. 세제곱에 가까운 곡선을 촘촘히 떠 스톱 사이가 꺾여 보이지 않게 한다. */
private val FadeStops =
    listOf(
        0f to 0f,
        0.25f to 0.03f,
        0.45f to 0.12f,
        0.62f to 0.28f,
        0.78f to 0.52f,
        0.9f to 0.78f,
        1f to 1f,
    )
