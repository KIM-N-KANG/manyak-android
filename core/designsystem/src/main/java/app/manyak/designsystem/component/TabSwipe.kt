package app.manyak.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 탭 내용 영역을 좌우로 밀어 옆 탭으로 넘긴다. 왼쪽으로 밀면 다음 탭, 오른쪽으로 밀면 이전 탭이다.
 *
 * 내용은 손가락을 반쯤 따라 밀리고, 폭의 1/4 을 넘기거나 빠르게 튕기면 넘어가며 새 탭이 민 쪽 반대편에서
 * 들어온다. 갈 탭이 없거나 [canSelect] 가 막은 쪽으로는 조금만 밀리고 제자리로 돌아온다.
 *
 * 탭마다 내용을 미리 그려 두는 `HorizontalPager` 대신 쓰는 것은, 탭을 쓰는 화면이 선택한 탭의 내용만
 * 그리고 스크롤 위치와 검증 이동을 탭 번호에 묶어 두었기 때문이다. 안쪽의 가로 스크롤(칩 줄 등)은
 * 먼저 끌림을 가져가므로 이 동작과 겹치지 않는다.
 *
 * [state] 를 넘기지 않으면 이 modifier 를 단 영역이 함께 밀린다. 탭 줄이 목록 안에 고정 머리로 있어
 * 미는 영역(목록 전체)과 밀릴 내용(탭 내용 항목)이 다르면, [rememberTabSwipeState] 를 넘기고 내용에
 * [tabSwipeOffset] 을 단다.
 */
@Composable
fun Modifier.swipeBetweenTabs(
    selectedIndex: Int,
    tabCount: Int,
    onSelect: (Int) -> Unit,
    canSelect: (Int) -> Boolean = { true },
    state: TabSwipeState? = null,
): Modifier {
    val scope = rememberCoroutineScope()
    val ownState = state ?: rememberTabSwipeState()
    val offset = ownState.offset
    val current by rememberUpdatedState(selectedIndex)
    val count by rememberUpdatedState(tabCount)
    val select by rememberUpdatedState(onSelect)
    val allowed by rememberUpdatedState(canSelect)
    val enterMillis = ManyakTheme.motion.elementEnterMillis
    return pointerInput(Unit) {
        val tracker = VelocityTracker()
        val flingVelocity = SwipeFlingVelocity.toPx()
        var dragged = 0f

        fun target(direction: Float): Int? =
            (current - direction.sign.toInt()).takeIf { it in 0 until count && it != current && allowed(it) }
        detectHorizontalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                dragged = 0f
            },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().x
                val next = target(dragged)
                val flung = abs(velocity) > flingVelocity && velocity.sign == dragged.sign
                if (next != null && (abs(dragged) > size.width * SWITCH_FRACTION || flung)) {
                    select(next)
                    scope.launch {
                        offset.snapTo(-dragged.sign * size.width * ENTER_FRACTION)
                        offset.animateTo(0f, tween(enterMillis, easing = LinearOutSlowInEasing))
                    }
                } else {
                    scope.launch { offset.animateTo(0f, spring()) }
                }
            },
            onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
        ) { change, amount ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            dragged += amount
            val follow = if (target(dragged) != null) DRAG_FOLLOW else EDGE_FOLLOW
            scope.launch { offset.snapTo(dragged * follow) }
        }
    }.then(if (state == null) Modifier.tabSwipeOffset(ownState) else Modifier)
}

/** 미는 영역과 밀릴 내용을 나눌 때 둘이 함께 보는 밀림 상태. */
@Stable
class TabSwipeState internal constructor() {
    internal val offset = Animatable(0f)
}

@Composable
fun rememberTabSwipeState(): TabSwipeState = remember { TabSwipeState() }

/** [state] 만큼 내용을 좌우로 민다. */
fun Modifier.tabSwipeOffset(state: TabSwipeState): Modifier = graphicsLayer { translationX = state.offset.value }

/** 손가락을 따라 밀리는 비율. 다 따라가면 탭 내용 전체가 끌려 다니는 것처럼 보인다. */
private const val DRAG_FOLLOW = 0.5f

/** 갈 탭이 없는 쪽으로 밀릴 때의 비율. 막혀 있다는 것만 느껴질 만큼이다. */
private const val EDGE_FOLLOW = 0.1f

/** 놓았을 때 넘어가는 끌린 거리. 내용 폭에 대한 비율이다. */
private const val SWITCH_FRACTION = 0.25f

/** 새 탭이 들어오기 시작하는 거리. 내용 폭에 대한 비율이다. */
private const val ENTER_FRACTION = 0.15f

/** 거리가 모자라도 이보다 빠르게 튕기면 넘어간다. `HorizontalPager` 의 기본 넘김 속도와 같다. */
private val SwipeFlingVelocity = 400.dp
