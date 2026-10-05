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
import app.manyak.legal.consent.presentation.ConsentLoadFailureContent
import app.manyak.legal.consent.presentation.LegalConsentIntent
import app.manyak.legal.consent.presentation.LegalConsentPhase
import app.manyak.legal.consent.presentation.LegalConsentSheet
import app.manyak.legal.consent.presentation.LegalConsentUiState
import app.manyak.legal.consent.presentation.LegalConsentViewModel

/** 로그인 중 필수 동의가 남으면 계정과 세션 없이 로그인 화면 위에서 같은 시트로 받는다. */
@Composable
internal fun SignupConsentGate(
    isSignup: Boolean,
    consentViewModel: LegalConsentViewModel,
) {
    AuthNavDisplay()
    LegalConsentSheet(enabled = isSignup, viewModel = consentViewModel)
}

/** 동의 조회와 저장을 마치기 전에는 회원 화면을 구성하지 않는다. */
@Composable
internal fun MemberConsentGate(
    state: LegalConsentUiState,
    isStartup: Boolean,
    consentConfirmed: Boolean,
    onIntent: (LegalConsentIntent) -> Unit,
    loginContent: @Composable () -> Unit,
    consentContent: @Composable () -> Unit,
    memberContent: @Composable () -> Unit,
) {
    if (consentConfirmed && state.isSatisfied && !state.isLoggingOut) {
        memberContent()
        return
    }
    val checking = state.phase == LegalConsentPhase.CHECKING
    val showProgress = rememberDelayedProgressVisibility(checking)
    if (isStartup) {
        val failed = state.phase == LegalConsentPhase.LOAD_FAILED || state.phase == LegalConsentPhase.FORBIDDEN
        StartupScreen(
            showProgress = showProgress,
            failureContent =
                if (failed) {
                    {
                        val forbidden = state.phase == LegalConsentPhase.FORBIDDEN
                        ConsentLoadFailureContent(
                            forbidden = forbidden,
                            enabled = !state.isLocked,
                            onRetry = {
                                onIntent(if (forbidden) LegalConsentIntent.Abandon else LegalConsentIntent.Retry)
                            },
                        )
                    }
                } else {
                    null
                },
        )
    } else {
        LoginConsentProgress(checking, showProgress, state.isLocked, onIntent, loginContent)
    }
    consentContent()
}

@Composable
private fun LoginConsentProgress(
    checking: Boolean,
    showProgress: Boolean,
    locked: Boolean,
    onIntent: (LegalConsentIntent) -> Unit,
    loginContent: @Composable () -> Unit,
) {
    ManyakTheme(darkTheme = true) {
        loginContent()
        BackHandler(enabled = checking && !locked) { onIntent(LegalConsentIntent.Abandon) }
        if (showProgress) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ManyakProgressIndicator()
            }
        }
    }
}
