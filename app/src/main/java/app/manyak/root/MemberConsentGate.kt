package app.manyak.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.consent.presentation.LegalConsentPhase
import app.manyak.legal.consent.presentation.LegalConsentUiState
import app.manyak.R as AppR

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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ManyakProgressIndicator()
                Text(
                    text = stringResource(AppR.string.member_consent_checking),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.text,
                )
            }
        }
    }
    consentContent()
}
