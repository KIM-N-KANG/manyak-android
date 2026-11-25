package app.manyak.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import app.manyak.designsystem.theme.ManyakTheme

/** 입력란 밖의 탭은 포커스를 해제하고, 입력란 이동·재터치·드래그는 그대로 둔다. */
fun Modifier.clearFocusOnTap(): Modifier = this then OutsideFocusTapElement

/** 입력란의 라벨·여백까지 바깥 탭 판정에서 제외한다. 터치 이벤트는 소비하지 않는다. */
fun Modifier.keepKeyboardOnTap(enabled: Boolean = true): Modifier = if (enabled) this then InputTapElement else this

private object OutsideFocusTapKey

private object InputTapElement : ModifierNodeElement<InputTapNode>() {
    override fun create() = InputTapNode()

    override fun update(node: InputTapNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "keepKeyboardOnTap"
    }

    override fun hashCode() = javaClass.hashCode()

    override fun equals(other: Any?) = other === this
}

private class InputTapNode : DelegatingNode() {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    traverseAncestors(OutsideFocusTapKey) {
                        (it as OutsideFocusTapNode).inputTapped = true
                        true
                    }
                }
            },
        )
    }
}

private object OutsideFocusTapElement : ModifierNodeElement<OutsideFocusTapNode>() {
    override fun create() = OutsideFocusTapNode()

    override fun update(node: OutsideFocusTapNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "clearFocusOnTap"
    }

    override fun hashCode() = javaClass.hashCode()

    override fun equals(other: Any?) = other === this
}

private class OutsideFocusTapNode :
    DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    TraversableNode {
    override val traverseKey: Any = OutsideFocusTapKey
    var inputTapped = false

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    inputTapped = false
                    var dragged = false
                    var pressed = true
                    while (pressed) {
                        // 자식 입력란의 포커스 이동과 버튼 동작이 끝난 뒤 판정한다. 이벤트는 소비하지 않는다.
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        dragged = dragged ||
                            event.changes.size > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        pressed = change.pressed
                    }
                    if (!dragged && !inputTapped) {
                        currentValueOf(LocalFocusManager).clearFocus()
                    }
                }
            },
        )
    }
}

/**
 * 포커스가 들어온 요소를 끌어올릴 때 그 아래로 남길 여백.
 *
 * 스크롤 컨테이너는 대상을 뷰포트 가장자리에 딱 맞춰 세운다. 키보드가 올라온 상태에서는 입력
 * 필드가 키보드에 붙어버리므로 목표 영역을 이만큼 키운다. 스크롤을 따로 요청하지 않고 판정
 * 기준만 바꾸는 것이 핵심이다 — 요청을 얹으면 컨테이너가 진행 중이던 애니메이션과 경쟁해
 * 두 번 튀는 스크롤이 된다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusScrollMargin(content: @Composable () -> Unit) {
    val marginPx = with(LocalDensity.current) { ManyakTheme.spacing.gutter.toPx() }
    val spec =
        remember(marginPx) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float = super.calculateScrollDistance(offset, size + marginPx, containerSize)
            }
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
