package app.manyak.designsystem.component

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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 앱의 바텀 시트. 모양과 안전 영역 처리, 그리고 **열리는 방식**을 이 컴포넌트가 소유한다.
 *
 * 절반 펼침을 막는 것이 핵심이다 — 기본 동작으로 두면 내용이 화면 절반을 넘는 순간 시트가 아래가
 * 잘린 채로 열려서, 맨 아래 버튼이 처음부터 보이지 않는다. 호출부마다 기억해야 하는 설정으로 두면
 * 빠뜨린 시트에서만 조용히 재발하므로 여기서 강제한다.
 *
 * 내용은 항상 스크롤된다. 큰 글자·작은 화면에서 내용이 시트 높이를 넘겨도 닿을 수 있어야 한다.
 *
 * @param dismissEnabled false 면 끌어내려 닫기를 막는다(예: 전송 중 — 결과를 못 본 채 사라지지 않게).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManyakBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    // SheetValue 는 실험 API 라 공개 시그니처에 두지 않는다 — 노출하면 호출부까지 OptIn 이 번진다.
    // 람다를 매 조합마다 새로 만들면 시트 상태가 다시 만들어지므로 한 번만 만들고 값만 갱신한다.
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val confirmValueChange =
        remember { { value: SheetValue -> currentDismissEnabled || value != SheetValue.Hidden } }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = confirmValueChange,
            ),
        containerColor = ManyakTheme.colors.surfaceRaised,
        shape = ManyakTheme.shapes.sheet,
        // 하단 안전 영역과 키보드 높이는 아래 본문이 직접 낀다.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { BottomSheetDefaults.DragHandle(color = ManyakTheme.colors.border) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    .verticalScroll(rememberScrollState())
                    // 위쪽은 드래그 핸들이 자체 여백을 갖고 있어 더 두지 않는다.
                    .padding(bottom = ManyakTheme.spacing.gutter),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
