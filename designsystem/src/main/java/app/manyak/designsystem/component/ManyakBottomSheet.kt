package app.manyak.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.flow.first

/**
 * 앱의 바텀 시트. 모양과 안전 영역 처리, 그리고 **열리는 방식**을 이 컴포넌트가 소유한다.
 *
 * 절반 펼침을 막는 것이 핵심이다 — 기본 동작으로 두면 내용이 화면 절반을 넘는 순간 시트가 아래가
 * 잘린 채로 열려서, 맨 아래 버튼이 처음부터 보이지 않는다. 호출부마다 기억해야 하는 설정으로 두면
 * 빠뜨린 시트에서만 조용히 재발하므로 여기서 강제한다.
 *
 * 내용은 항상 스크롤된다. 큰 글자·작은 화면에서 내용이 시트 높이를 넘겨도 닿을 수 있어야 한다.
 *
 * @param dismissEnabled false 면 끌어내리기·스크림 탭으로 닫는 것을 막고 핸들 드래그 자체도 잠근다(예: 전송 중 —
 * 결과를 못 본 채 사라지지 않게). 닫히지 않는 시트가 끌리기만 하면 튕기는 움직임이 "닫을 수 있다" 는 신호가 된다.
 * @param dismissOnBackPress false 면 뒤로가기도 막는다. 기본은 [dismissEnabled] 와 같다. 뒤로가기는
 * 끌어내리기 판정을 거치지 않고 [onDismissRequest] 를 부르므로 따로 잠가야 한다. 시트가 열려 있는 동안 바뀌어도 바로 따른다.
 * @param onExpanded 시트가 완전히 펼쳐진 뒤 호출한다. 시트 위 안내가 움직이는 행을 가리지 않게 한다.
 * @param dismissOnContentDrag false 면 본문을 끌어도 시트가 내려가지 않고 핸들 끌기, 바깥 탭과 뒤로가기로만 닫힌다.
 * 본문 안 목록을 끝까지 끌다가 시트까지 닫히는 일을 막는다.
 * @param dragHandleVisible false 면 핸들을 두지 않는다. 끌어내릴 수 없는 시트(필수 동의)에 핸들이 있으면 닫을 수
 * 있다는 신호가 되어서다. 전송 중처럼 잠깐 잠그는 시트는 핸들이 사라졌다 나타나지 않게 그대로 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManyakBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    dismissOnBackPress: Boolean = dismissEnabled,
    dismissOnContentDrag: Boolean = true,
    dragHandleVisible: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onExpanded: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // SheetValue 는 실험 API 라 공개 시그니처에 두지 않는다 — 노출하면 호출부까지 OptIn 이 번진다.
    // 람다를 매 조합마다 새로 만들면 시트 상태가 다시 만들어지므로 한 번만 만들고 값만 갱신한다.
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val confirmValueChange =
        remember { { value: SheetValue -> currentDismissEnabled || value != SheetValue.Hidden } }

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = confirmValueChange,
        )
    val currentOnExpanded by rememberUpdatedState(onExpanded)
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
        currentOnExpanded()
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.sheet,
        // 하단 안전 영역과 키보드 높이는 아래 본문이 직접 낀다.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        sheetGesturesEnabled = dismissEnabled,
        properties =
            ModalBottomSheetProperties(
                // 창의 뒤로가기 콜백은 시트를 열 때 값으로 고정되어 이후 변경을 따르지 않는다. 창 콜백은 늘 켜 두고
                // 잠글 때는 본문의 BackHandler 가 먼저 받아 삼킨다.
                shouldDismissOnBackPress = true,
                shouldDismissOnClickOutside = dismissEnabled,
            ),
        dragHandle =
            if (dragHandleVisible) {
                {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier.clearFocusOnTap(),
                        color = ManyakTheme.colors.border,
                    )
                }
            } else {
                null
            },
    ) {
        BackHandler(enabled = !dismissOnBackPress) {}
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clearFocusOnTap()
                    .imePadding()
                    .navigationBarsPadding()
                    .then(if (dismissOnContentDrag) Modifier else Modifier.nestedScroll(KeepSheetOnContentDrag))
                    // 좌우 여백도 스크롤 안에 두어 여백을 끌어도 본문 끌기로 처리한다.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    // 위쪽은 드래그 핸들이 자체 여백을 갖고 있어 핸들이 없을 때만 둔다.
                    .padding(
                        top = if (dragHandleVisible) 0.dp else ManyakTheme.spacing.gutter,
                        bottom = ManyakTheme.spacing.gutter,
                    ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** 본문 스크롤이 쓰고 남은 끌기와 플링을 시트에 넘기지 않는다. 핸들은 본문 밖이라 그대로 끌린다. */
private object KeepSheetOnContentDrag : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = available
}
