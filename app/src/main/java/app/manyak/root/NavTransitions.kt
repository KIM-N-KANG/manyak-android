package app.manyak.root

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 화면·탭이 바뀔 때의 교차 페이드.
 *
 * 기본값(700ms)은 하단 탭처럼 자주 오가는 전환에서 눌렀는데 뒤늦게 따라오는 느낌을 준다.
 * 예측형 뒤로가기는 손가락을 따라오는 제스처 피드백 그 자체이므로 여기서 바꾸지 않고 기본값을 쓴다.
 */
@Composable
fun rememberScreenTransition(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform {
    val durationMillis = ManyakTheme.motion.screenTransitionMillis
    return remember(durationMillis) {
        { fadeIn(tween(durationMillis)) togetherWith fadeOut(tween(durationMillis)) }
    }
}
