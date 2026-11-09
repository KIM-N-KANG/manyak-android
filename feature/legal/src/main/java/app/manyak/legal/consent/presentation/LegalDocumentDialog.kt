package app.manyak.legal.consent.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.manyak.core.navigation.LegalDocument
import app.manyak.legal.presentation.LegalDocumentScreen
import app.manyak.legal.presentation.LegalViewModel

/**
 * 시트 위에서 여는 전문. 시트와 같은 창 계층이라 나중에 만든 이 창이 위에 놓이고, 닫으면 체크 상태 그대로 시트로 돌아온다.
 * 앱바의 뒤로가기와 시스템 뒤로가기 모두 창만 닫는다.
 * 문서별로 ViewModel 키를 갈라 약관 다음에 처리방침을 열어도 이전 문서가 남지 않게 한다.
 */
@Composable
internal fun LegalDocumentDialog(
    document: LegalDocument,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        LegalDocumentScreen(
            document = document,
            onBack = onClose,
            viewModel =
                hiltViewModel<LegalViewModel, LegalViewModel.Factory>(
                    key = document.name,
                    creationCallback = { factory -> factory.create(document) },
                ),
        )
    }
}
