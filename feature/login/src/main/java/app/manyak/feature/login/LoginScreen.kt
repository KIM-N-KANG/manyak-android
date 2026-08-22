package app.manyak.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.ui.R
import app.manyak.core.ui.error.messageResOrNull
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 앱은 로그인 필수라 이 화면이 첫 실행 경험이다(하네스 §3-3-1).
 *
 * 문구 정본은 웹(FE-SCREEN-008)이며 앱이 새로 쓰지 않는다. 게스트 데이터가 없으므로 웹의 이관 1회
 * 안내만 뺀다.
 */
@Composable
fun LoginScreen(
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LoginContent(
        state = state,
        onSignIn = { provider -> viewModel.onIntent(LoginIntent.SignIn(provider)) },
        onOpenLegalDocument = onOpenLegalDocument,
        modifier = modifier,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onSignIn: (AuthProvider) -> Unit,
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ManyakTheme.colors.surface,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CenteredMessage(
                textRes = R.string.login_headline,
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
            )
            state.notice?.messageResOrNull()?.let { noticeRes ->
                CenteredMessage(
                    modifier = Modifier.padding(top = ManyakTheme.spacing.section),
                    textRes = noticeRes,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textDanger,
                )
            }

            ProviderButtons(
                modifier = Modifier.padding(top = ManyakTheme.spacing.block),
                state = state,
                onSignIn = onSignIn,
            )

            CenteredMessage(
                modifier = Modifier.padding(top = ManyakTheme.spacing.section),
                textRes = R.string.login_existing_account,
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
            CenteredMessage(
                modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                textRes = R.string.login_provider_conflict,
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
            state.error?.messageResOrNull()?.let { errorRes ->
                CenteredMessage(
                    modifier = Modifier.padding(top = ManyakTheme.spacing.component),
                    textRes = errorRes,
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textDanger,
                )
            }

            LegalConsent(
                modifier = Modifier.padding(top = ManyakTheme.spacing.section),
                onOpenLegalDocument = onOpenLegalDocument,
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    textRes: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = stringResource(textRes),
        style = style,
        color = color,
        textAlign = TextAlign.Center,
    )
}

/** 카카오가 위, Google 이 아래다(공통 계약 FE-SCREEN-008). */
@Composable
private fun ProviderButtons(
    state: LoginUiState,
    onSignIn: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        ProviderButton(
            provider = AuthProvider.KAKAO,
            labelRes = R.string.login_kakao,
            logoRes = R.drawable.ic_logo_kakao,
            containerColor = KakaoContainerColor,
            contentColor = KakaoContentColor,
            state = state,
            onClick = onSignIn,
        )
        ProviderButton(
            provider = AuthProvider.GOOGLE,
            labelRes = R.string.login_google,
            logoRes = R.drawable.ic_logo_google,
            containerColor = ManyakTheme.colors.surfaceRaised,
            contentColor = ManyakTheme.colors.text,
            state = state,
            onClick = onSignIn,
        )
    }
}

@Composable
private fun ProviderButton(
    provider: AuthProvider,
    labelRes: Int,
    logoRes: Int,
    containerColor: Color,
    contentColor: Color,
    state: LoginUiState,
    onClick: (AuthProvider) -> Unit,
) {
    // 진행 중에는 탭한 버튼을 스피너로 바꾸고 두 버튼을 모두 비활성화한다(공통 계약).
    val isBusy = state.inProgress != null
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(provider) },
        enabled = !isBusy,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor,
            ),
    ) {
        if (state.inProgress == provider) {
            CircularProgressIndicator(modifier = Modifier.size(LogoSize), color = contentColor)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                Image(painter = painterResource(logoRes), contentDescription = null, modifier = Modifier.size(LogoSize))
                Text(text = stringResource(labelRes), style = ManyakTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LegalConsent(
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            text = stringResource(R.string.login_consent),
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
            LegalLink(R.string.login_consent_terms) { onOpenLegalDocument(LegalDocument.TERMS) }
            LegalLink(R.string.login_consent_privacy) { onOpenLegalDocument(LegalDocument.PRIVACY) }
        }
    }
}

@Composable
private fun LegalLink(
    labelRes: Int,
    onClick: () -> Unit,
) {
    Text(
        modifier = Modifier.clickable(onClick = onClick).padding(ManyakTheme.spacing.hairline),
        text = stringResource(labelRes),
        style = ManyakTheme.typography.labelSmall,
        color = ManyakTheme.colors.textBrand,
    )
}

private val LogoSize = 20.dp
private val KakaoContainerColor = Color(0xFFFEE500)
private val KakaoContentColor = Color(0xE6000000)

@Preview(showBackground = true, name = "로그인 · 라이트")
@Composable
private fun LoginScreenPreview() {
    ManyakTheme(darkTheme = false) {
        LoginContent(state = LoginUiState(), onSignIn = {}, onOpenLegalDocument = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131313, name = "로그인 · 다크")
@Composable
private fun LoginScreenDarkPreview() {
    ManyakTheme(darkTheme = true) {
        LoginContent(state = LoginUiState(inProgress = AuthProvider.KAKAO), onSignIn = {}, onOpenLegalDocument = {})
    }
}
