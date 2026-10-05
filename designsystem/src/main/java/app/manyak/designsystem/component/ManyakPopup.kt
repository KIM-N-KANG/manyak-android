package app.manyak.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 앵커 아래에 뜨는 메뉴와 팝오버. 앵커 쪽 위 변에서 살짝 작고 위에 있던 판이 튕김 없는 스프링으로 자리를 잡고,
 * 투명도는 그보다 먼저 차올라 판이 움직이는 동안 이미 읽힌다. 닫을 때는 짧게 흐려지며 조금만 줄어든다 —
 * 볼 일이 끝난 판이 길게 남으면 기다리게 만든다. 닫는 동작이 끝날 때까지 창을 남겨 두므로 [visible] 로만 여닫는다.
 */
@Composable
fun ManyakPopup(
    visible: Boolean,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    properties: PopupProperties = PopupProperties(),
    transformOrigin: TransformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f),
    content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    val slidePx = with(LocalDensity.current) { PopupSlide.roundToPx() }
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        AnimatedVisibility(
            visibleState = state,
            enter =
                fadeIn(tween(POPUP_FADE_IN_MILLIS, easing = LinearOutSlowInEasing)) +
                    scaleIn(popupSpring(), POPUP_ENTER_SCALE, transformOrigin) +
                    slideInVertically(popupSpring()) { -slidePx },
            exit =
                fadeOut(tween(POPUP_EXIT_MILLIS, easing = FastOutLinearInEasing)) +
                    scaleOut(
                        tween(POPUP_EXIT_MILLIS, easing = FastOutLinearInEasing),
                        POPUP_EXIT_SCALE,
                        transformOrigin,
                    ),
        ) {
            content()
        }
    }
}

/** 튕김 없이 약 0.3초에 자리 잡는다. 첫 프레임이 늦어도 남은 움직임이 끊겨 보이지 않을 만큼의 길이다. */
private fun <T> popupSpring() =
    spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

private const val POPUP_FADE_IN_MILLIS = 150
private const val POPUP_EXIT_MILLIS = 120
private const val POPUP_ENTER_SCALE = 0.94f
private const val POPUP_EXIT_SCALE = 0.97f
private val PopupSlide = 6.dp
