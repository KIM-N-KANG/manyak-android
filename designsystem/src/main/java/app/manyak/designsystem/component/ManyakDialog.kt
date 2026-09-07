package app.manyak.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 앱 다이얼로그의 창. 모양·배경만 소유하고 안쪽 여백은 내용이 갖는다 — 옵션 목록과 확인 문구는
 * 여백이 달라서다.
 *
 * 창 하나 안에서 내용을 갈아 끼울 수 있게 한 것이 핵심이다. 옵션 다이얼로그에서 "삭제하기"를 눌러
 * 확인으로 넘어갈 때 창을 닫고 새로 열면 스크림이 두 번 페이드돼 화면이 번쩍인다.
 */
@Composable
fun ManyakDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = ManyakTheme.shapes.overlay,
            color = ManyakTheme.colors.surfaceRaised,
        ) {
            // 내용이 바뀌며 높이가 달라질 때 창이 툭 줄지 않고 따라간다.
            Column(modifier = Modifier.animateContentSize(), content = content)
        }
    }
}
