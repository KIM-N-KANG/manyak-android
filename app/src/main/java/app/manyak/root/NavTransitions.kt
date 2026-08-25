package app.manyak.root

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.manyak.core.ui.theme.ManyakTheme

/** 제작 퍼널 목적지임을 알리는 엔트리 표식. 전환 판정이 이것만 본다. */
private const val FUNNEL_SCREEN_KEY = "app.manyak.root.funnelScreen"

/** 퍼널 목적지의 `NavEntry` metadata. 표식이 없는 목적지는 퍼널 밖으로 본다. */
fun funnelScreenMetadata(): Map<String, Any> = mapOf(FUNNEL_SCREEN_KEY to true)

/** 퍼널 경계를 넘는 전환의 방향. 경계를 넘지 않으면 [NONE] 이고 기존 교차 페이드를 쓴다. */
enum class FunnelTransitionDirection {
    ENTER,
    EXIT,
    NONE,
}

internal fun funnelTransitionDirection(
    fromFunnel: Boolean,
    toFunnel: Boolean,
): FunnelTransitionDirection =
    when {
        fromFunnel == toFunnel -> FunnelTransitionDirection.NONE
        toFunnel -> FunnelTransitionDirection.ENTER
        else -> FunnelTransitionDirection.EXIT
    }

/** `Scene.metadata` 는 마지막 엔트리의 metadata 를 그대로 노출한다. */
private fun Scene<*>.isFunnel(): Boolean = metadata[FUNNEL_SCREEN_KEY] == true

/**
 * 화면·탭 전환.
 *
 * 기본은 교차 페이드다. 제작 퍼널은 홈 위에 얹히는 한 덩어리로 읽혀야 하므로 퍼널 경계를
 * 넘을 때만 수직으로 밀어 올리고 내린다. 퍼널 단계 사이 이동까지 수직이면 "다음 단계"와
 * "퍼널 진입"이 같은 모션이 되어 구분되지 않는다.
 *
 * 기본 지속 시간(700ms)은 하단 탭처럼 자주 오가는 전환에서 눌렀는데 뒤늦게 따라오는 느낌을 준다.
 * 예측형 뒤로가기는 손가락을 따라오는 제스처 피드백 그 자체이므로 여기서 바꾸지 않고 기본값을 쓴다.
 */
@Composable
fun rememberScreenTransition(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform {
    val durationMillis = ManyakTheme.motion.screenTransitionMillis
    return remember(durationMillis) {
        {
            val spec = tween<Float>(durationMillis)
            when (funnelTransitionDirection(initialState.isFunnel(), targetState.isFunnel())) {
                FunnelTransitionDirection.NONE -> fadeIn(spec) togetherWith fadeOut(spec)

                // 들어오는 퍼널을 위에 얹고 아래 화면은 그대로 둔다. 아래를 함께 페이드하면
                // 퍼널이 덮는 게 아니라 두 화면이 동시에 바뀌는 것으로 보인다.
                FunnelTransitionDirection.ENTER ->
                    (
                        slideInVertically(tween(durationMillis)) { height -> height } togetherWith
                            ExitTransition.None
                    ).apply { targetContentZIndex = FUNNEL_Z_INDEX }

                // 나가는 퍼널이 위에 남은 채 내려가고 아래 화면이 드러난다.
                FunnelTransitionDirection.EXIT ->
                    (
                        EnterTransition.None togetherWith
                            slideOutVertically(tween(durationMillis)) { height -> height }
                    ).apply { targetContentZIndex = BASE_Z_INDEX }
            }
        }
    }
}

private const val FUNNEL_Z_INDEX = 1f
private const val BASE_Z_INDEX = 0f
