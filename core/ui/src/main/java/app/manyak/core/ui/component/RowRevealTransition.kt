package app.manyak.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 사용자가 늘리고 줄이는 목록에서 칸 하나의 등장·퇴장. 새로 더한 칸은 아래에서 자라 오르고, 지운
 * 칸은 같은 변을 붙잡은 채 접힌다.
 *
 * 접힘이 끝나면 [onExited] 로 알린다. **목록에서 빼는 것은 그때다** — 먼저 빼면 컴포저블이 사라져
 * 애니메이션이 아예 나오지 않는다. 칸마다 [androidx.compose.runtime.key] 로 감싸 부른다. 상태가 옆
 * 칸으로 옮겨 붙으면 지운 칸 대신 다른 칸이 접힌다.
 *
 * 목록 전체가 한꺼번에 갈리는 경로(복원·초기화·모드 전환)는 이 표시를 거치지 않게 둔다 — 한 칸씩
 * 접히면 우수수 무너지는 것처럼 보인다.
 */
@Composable
fun RowRevealTransition(
    entering: Boolean,
    exiting: Boolean,
    onExited: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enterMillis = ManyakTheme.motion.elementEnterMillis
    val exitMillis = ManyakTheme.motion.elementExitMillis
    val visibleState = remember { MutableTransitionState(!entering).apply { targetState = true } }
    LaunchedEffect(exiting) { visibleState.targetState = !exiting }
    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) onExited()
    }
    AnimatedVisibility(
        modifier = modifier,
        visibleState = visibleState,
        // 아래 변을 붙잡고 높이를 키우고 줄인다. 위를 붙잡으면 위 칸들이 밀렸다 당겨진다.
        enter =
            expandVertically(
                animationSpec = tween(enterMillis, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(animationSpec = tween(enterMillis)),
        exit =
            shrinkVertically(
                animationSpec = tween(exitMillis, easing = FastOutLinearInEasing),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(animationSpec = tween(exitMillis)),
        content = { content() },
    )
}
