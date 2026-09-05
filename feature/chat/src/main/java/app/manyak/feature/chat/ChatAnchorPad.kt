package app.manyak.feature.chat

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.flow.filterNotNull

/** 목록 끝 패드의 항목 키. 앵커 계산은 콘텐츠 끝을 잴 때 이 항목만 빼야 한다. */
internal const val ANCHOR_PAD_KEY = "bottom"

/**
 * 앵커 아래에 깔아야 할 자리의 높이.
 *
 * 마지막 항목은 그 아래에 콘텐츠가 없어 목록을 끝까지 내려도 상단까지 올라오지 못한다. 앵커부터 목록
 * 끝까지가 한 화면에 **모자란 만큼**을 빈 자리로 깔아 두면 비로소 올라올 수 있다. 조각이 붙어 콘텐츠가
 * 자랄수록 이 값은 0으로 수렴하고, 한 화면을 넘어서면 자리는 필요 없어진다.
 */
internal fun requiredAnchorPad(
    viewportHeight: Int,
    anchorOffset: Int,
    contentEndOffset: Int,
): Int = (viewportHeight - (contentEndOffset - anchorOffset)).coerceAtLeast(0)

/**
 * 화면을 밀지 않고 패드를 줄일 수 있는 하한.
 *
 * 뷰포트 안에 보이는 부분까지 없애면 그만큼 아래 콘텐츠가 위로 당겨져 읽던 자리가 튄다. 아래로 넘어간
 * 부분만 회수하면 스크롤 길이만 줄고 화면은 그대로다.
 */
internal fun anchorPadFloor(
    viewportEndOffset: Int,
    padOffset: Int,
): Int = (viewportEndOffset - padOffset).coerceAtLeast(0)

/**
 * 진행 중인 턴을 뷰포트 맨 위에 붙인다 — 조각이 늘어도 읽던 자리가 끌려 내려가지 않는다.
 *
 * **자리를 만드는 것과 앵커는 같은 측정 패스에 적용한다.** 순서를 나누면 자리가 없어 바닥에 걸리거나,
 * 기다리는 사이 프레임에 옛 위치가 그려져 깜빡인다. 앵커된 뒤에는 목록이 (인덱스, 오프셋)으로 위치를
 * 잡으므로 조각이 붙어도 저절로 유지되고, 채워진 만큼 패드를 줄여 스크롤이 콘텐츠 밖으로 늘어나지
 * 않게 한다.
 *
 * **뷰포트 높이가 바뀌면 다시 잡는다** — 키보드가 내려가 화면이 커지면 깔아 둔 자리가 한 화면에
 * 모자라져 목록이 앵커를 놓고 되돌아간다. 사용자 스크롤은 높이를 바꾸지 않으므로 되잡기와 싸우지 않는다.
 *
 * 재생성은 목록 끝이 아니라 **대상 턴 자리**를 맞춘다. 바뀌는 곳이 화면 밖이면 무엇이 다시 만들어지는지
 * 보이지 않는다.
 */
@Composable
internal fun AnchorStreamingTurn(
    listState: LazyListState,
    state: ChatRoomUiState,
    itemCount: Int,
    prologueCount: Int,
    padPx: MutableIntState,
) {
    val anchorIndex = anchorIndexOf(state = state, itemCount = itemCount, prologueCount = prologueCount)
    val anchor = rememberSaveable(saver = AnchorState.Saver) { AnchorState() }

    // 첫 앵커는 **전송이 그려지는 프레임 안**이어야 한다. LaunchedEffect 코루틴은 다음 프레임에 돌아
    // 옛 위치가 한 번 그려지고(깜빡임), SideEffect 는 컴포지션 적용 직후·그리기 전에 동기로 돈다.
    SideEffect {
        if (!state.isStreaming) {
            anchor.anchored = false
            return@SideEffect
        }
        if (anchor.anchored) return@SideEffect
        anchor.anchored = true
        val viewport = listState.layoutInfo.viewportHeight()
        if (viewport > 0) {
            padPx.intValue = viewport
            anchor.viewport = viewport
        }
        // scrollToItem 이 아니라 **다음 측정에 미뤄 적용하는** 호출이어야 한다. 즉시 스크롤은 아직 옛
        // 패드 높이로 재서 바닥에 걸린다. 미루면 새 패드와 같은 측정 패스에서 앵커된다.
        listState.requestScrollToItem(anchorIndex)
    }

    LaunchedEffect(state.isStreaming) {
        if (!state.isStreaming) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }.collect { info ->
            val viewport = info.viewportHeight()
            if (viewport <= 0) return@collect
            if (viewport != anchor.viewport) {
                anchor.viewport = viewport
                // 되잡을 때는 한 화면을 통째로 깐다 — 키보드처럼 높이가 이어서 변하는 동안에는 계산한
                // 최소치가 다음 레이아웃에서 이미 모자라다.
                padPx.intValue = viewport
                listState.requestScrollToItem(anchorIndex)
                return@collect
            }
            info.anchorPadTarget(anchorIndex)?.let { target -> padPx.intValue = target }
        }
    }
}

/**
 * 스트리밍이 끝난 뒤 남은 패드를 **화면 밖에 있는 만큼만** 줄인다.
 *
 * 응답이 짧으면 만들어 둔 자리가 그대로 빈 공간으로 남는다. 한 번에 없애면 보고 있던 본문이 위로 튀므로,
 * 넘어간 부분만 회수해 스크롤 길이만 줄인다. 늘리지는 않는다 — 다음 전송이 자리를 다시 만든다.
 */
@Composable
internal fun ReclaimAnchorPad(
    listState: LazyListState,
    isStreaming: Boolean,
    padPx: MutableIntState,
) {
    LaunchedEffect(isStreaming) {
        if (isStreaming) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.padFloor() }
            .filterNotNull()
            .collect { floor -> padPx.intValue = padPx.intValue.coerceAtMost(floor) }
    }
}

/**
 * 목록 끝 패드.
 *
 * **높이를 레이아웃 단계에서 읽는다** — 조각마다 바뀌는 값이라 컴포지션에서 읽으면 토큰이 붙을 때마다
 * 재구성이 한 번 더 돈다. 여기서 필요한 것은 다시 재는 것뿐이다.
 */
@Composable
internal fun AnchorPad(padPx: IntState) {
    val minHeight = with(LocalDensity.current) { ManyakTheme.spacing.passage.roundToPx() }
    Spacer(
        modifier =
            Modifier.fillMaxWidth().layout { measurable, constraints ->
                val height = maxOf(padPx.intValue, minHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
    )
}

/**
 * 스트림 하나의 앵커 기록 — 이미 걸었는지와 그때 기준으로 삼은 뷰포트 높이. 스냅샷 상태가 아니라
 * 재구성을 만들지 않는다.
 *
 * **구성 변경을 견뎌야 한다.** 재생성 직후의 첫 컴포지션에서는 목록을 아직 재지 않아 뷰포트가 0이라,
 * 표식을 잃고 앵커를 다시 걸면 자리를 깔지 못한 채 스크롤만 요청해 콘텐츠 끝에 걸린다. 기준 높이까지
 * 복원해야 되잡기가 실제로 높이가 바뀐 축(회전)에서만 돈다.
 */
private class AnchorState(
    var anchored: Boolean = false,
    var viewport: Int = 0,
) {
    companion object {
        val Saver: Saver<AnchorState, Any> =
            listSaver(
                save = { anchor -> listOf<Any>(anchor.anchored, anchor.viewport) },
                restore = { saved -> AnchorState(anchored = saved[0] as Boolean, viewport = saved[1] as Int) },
            )
    }
}

/** 스트리밍 블록이 놓인 자리. 이어쓰기는 목록 끝(패드 앞), 재생성은 대상 턴 자리다. */
private fun anchorIndexOf(
    state: ChatRoomUiState,
    itemCount: Int,
    prologueCount: Int,
): Int {
    val regenerating = state.regeneratingTurnId ?: return (itemCount - 2).coerceAtLeast(0)
    val turnIndex = state.turns.indexOfFirst { turn -> turn.id == regenerating }
    return (prologueCount + turnIndex).coerceAtLeast(0)
}

private fun LazyListLayoutInfo.viewportHeight(): Int = viewportEndOffset - viewportStartOffset

/** 앵커가 보이지 않는 동안은 `null`. 모르는 값을 0으로 치면 앵커가 아래로 끌려 내려간다. */
private fun LazyListLayoutInfo.anchorPadTarget(anchorIndex: Int): Int? {
    val anchor = visibleItemsInfo.firstOrNull { item -> item.index == anchorIndex } ?: return null
    val last = visibleItemsInfo.lastOrNull { item -> item.key != ANCHOR_PAD_KEY } ?: return null
    return requiredAnchorPad(
        viewportHeight = viewportHeight(),
        anchorOffset = anchor.offset,
        contentEndOffset = last.offset + last.size,
    )
}

/** 아직 재지 않았으면 `null`, 패드가 화면 아래로 완전히 넘어갔으면 0. */
private fun LazyListLayoutInfo.padFloor(): Int? {
    if (visibleItemsInfo.isEmpty()) return null
    val pad = visibleItemsInfo.lastOrNull { item -> item.key == ANCHOR_PAD_KEY } ?: return 0
    return anchorPadFloor(viewportEndOffset = viewportEndOffset, padOffset = pad.offset)
}
