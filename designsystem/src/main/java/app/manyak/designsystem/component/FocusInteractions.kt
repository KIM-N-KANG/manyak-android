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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 클릭 가능한 자식이 이벤트를 소비하기 전에 기존 입력 포커스를 해제한다.
 *
 * 손가락이 슬롭 안에 머문 채 떨어진 제스처만 탭으로 본다. 눌림만 보고 지우면 목록을 넘기려는
 * 첫 접촉에도 포커스가 풀려 입력 중에 스크롤을 할 수 없다.
 *
 * **Initial 패스에서 보기만 하고 소비하지 않는다** — 스크롤과 항목 클릭이 그대로 동작한다.
 */
fun Modifier.clearFocusOnTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dragged = false
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    dragged = true
                }
                pressed = change?.pressed == true
            }
            if (!dragged) focusManager.clearFocus()
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
