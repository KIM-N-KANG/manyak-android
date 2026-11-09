package app.manyak.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 옵션 항목([ManyakOptionItem])을 담는 바텀 시트. 카드 옵션·상세 옵션·채팅 메뉴가 같은 틀을 쓴다 —
 * 카드는 앵커가 손가락 아래라 팝업이 카드를 가리고, 헤더 드롭다운은 항목이 늘면 좁아서 시트로 통일했다.
 *
 * 열림 여부는 호출부가 든다 — 어느 카드의 시트인지가 곧 화면 상태라 회전에서 살아남아야 한다.
 * 신고·삭제 확인처럼 다른 모달을 여는 항목은 시트를 먼저 닫는다 — 시트 위에 다이얼로그를 겹치면
 * 바깥 탭 판정이 서로 얽힌다.
 *
 * 닫기 버튼은 두지 않는다 — 주 동작 버튼이 없는 시트에서 닫기 하나뿐인 줄은 자리만 차지하고, 항목을
 * 고르거나 스크림·끌어내리기·뒤로가기로 닫는다.
 *
 * @param dismissEnabled false 면 끌어내려 닫기를 막는다(예: 새 채팅 생성 중).
 * @param header 머리글. [ManyakOptionsSheetHeader] 가 기본 구성이다.
 */
@Composable
fun ManyakOptionsSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        dismissEnabled = dismissEnabled,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        header()
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * 시트 머리글. [kind] 는 제목 위에 작은 보조색으로 놓여 카드 여러 장 중 어느 종류의 무엇을 골랐는지
 * 시트 안에서 확인하게 한다. 채팅 메뉴처럼 대상이 화면 자체인 시트는 [kind] 없이 제목만 둔다.
 */
@Composable
fun ManyakOptionsSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    kind: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
        if (kind != null) {
            Text(text = kind, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.textSubtle)
        }
        Text(
            text = title,
            style = ManyakTheme.typography.titleLarge,
            color = ManyakTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
