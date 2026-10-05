package app.manyak.root

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.manyak.designsystem.theme.ManyakTheme

typealias SceneTransform = AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform

/**
 * 스택 위에 쌓이고 걷히는 화면의 밀기 전환.
 *
 * 쌓이는 화면은 오른쪽에서 폭 전체를 밀고 들어오고, 아래 깔리는 화면은 폭의 일부만 왼쪽으로
 * 물러나 깊이가 읽힌다. 걷힐 때는 그 반대다. 예측형 뒤로가기는 같은 변환을 손가락 진행도로
 * 되감는 것이라 별도 모양을 두지 않는다.
 */
@Immutable
class ScreenSlideTransitions internal constructor(
    val push: SceneTransform,
    val pop: SceneTransform,
    val predictivePop: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform,
)

@Composable
fun rememberScreenSlideTransitions(): ScreenSlideTransitions {
    val durationMillis = ManyakTheme.motion.screenSlideMillis
    return remember(durationMillis) {
        ScreenSlideTransitions(
            push = { slideForward(durationMillis) },
            pop = { slideBack(durationMillis) },
            predictivePop = { slideBack(durationMillis) },
        )
    }
}

/**
 * 탭이 바뀔 때의 교차 페이드.
 *
 * 탭 전환은 이동이 아니라 어느 백스택을 그릴지 고르는 일이라 방향이 없고, 라이브러리 기본값(700ms)은
 * 자주 오가는 자리에서 눌렀는데 뒤늦게 따라오는 느낌을 준다.
 */
@Composable
fun rememberTabCrossfade(): SceneTransform {
    val durationMillis = ManyakTheme.motion.screenTransitionMillis
    return remember(durationMillis) {
        { fadeIn(tween(durationMillis)) togetherWith fadeOut(tween(durationMillis)) }
    }
}

/** 아래 깔리는 화면이 물러나는 거리 — 폭의 1/4. 이보다 크면 두 화면이 같은 속도로 흘러 깊이가 사라진다. */
private const val PARALLAX_DIVISOR = 4

/** 덮인 화면이 어두워지는 정도. 위 화면이 걷힐 때 같은 값에서 되살아난다. */
private const val COVERED_ALPHA = 0.6f

/** 쌓이는 화면은 불투명하게 밀려 들어온다 — 페이드를 섞으면 겹치는 동안 아래 화면이 비친다. */
private fun slideForward(durationMillis: Int): ContentTransform =
    slideInHorizontally(tween(durationMillis)) { it } togetherWith
        (
            slideOutHorizontally(tween(durationMillis)) { -it / PARALLAX_DIVISOR } +
                fadeOut(tween(durationMillis), targetAlpha = COVERED_ALPHA)
        )

private fun slideBack(durationMillis: Int): ContentTransform =
    (
        slideInHorizontally(tween(durationMillis)) { -it / PARALLAX_DIVISOR } +
            fadeIn(tween(durationMillis), initialAlpha = COVERED_ALPHA)
    ) togetherWith slideOutHorizontally(tween(durationMillis)) { it }
