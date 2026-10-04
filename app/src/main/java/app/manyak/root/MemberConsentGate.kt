package app.manyak.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.consent.presentation.LegalConsentPhase
import app.manyak.legal.consent.presentation.LegalConsentUiState

/** 동의 조회와 저장을 마치기 전에는 회원 화면을 구성하지 않는다. */
@Composable
internal fun MemberConsentGate(
    state: LegalConsentUiState,
    consentConfirmed: Boolean,
    onAbandon: () -> Unit,
    loginContent: @Composable () -> Unit,
    consentContent: @Composable () -> Unit,
    memberContent: @Composable () -> Unit,
) {
    if (consentConfirmed && state.isSatisfied && !state.isLoggingOut) {
        memberContent()
        return
    }
    ManyakTheme(darkTheme = true) {
        loginContent()
        val checking = state.phase == LegalConsentPhase.CHECKING
        BackHandler(enabled = checking && !state.isLocked, onBack = onAbandon)
        if (rememberDelayedProgressVisibility(checking)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ManyakProgressIndicator()
            }
        }
    }
    consentContent()
}
