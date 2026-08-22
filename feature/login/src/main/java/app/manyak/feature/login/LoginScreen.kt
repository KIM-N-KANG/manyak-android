package app.manyak.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
 * 안내는 넣지 않는다.
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
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(modifier = Modifier.height(ManyakTheme.spacing.block))
            Image(
                modifier = Modifier.height(ManyakTheme.sizes.logo).aspectRatio(LOGO_ASPECT_RATIO),
                painter = painterResource(R.drawable.ic_logo_manyak),
                contentDescription = stringResource(R.string.app_logo_description),
            )
            Text(
                modifier = Modifier.padding(top = ManyakTheme.spacing.component),
                text = stringResource(R.string.login_headline),
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
            )

            // 버튼과 안내는 화면 아래에 모은다.
            Spacer(modifier = Modifier.weight(1f))

            state.notice?.messageResOrNull()?.let { noticeRes ->
                CenteredMessage(
                    modifier = Modifier.padding(bottom = ManyakTheme.spacing.component),
                    text = stringResource(noticeRes),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textDanger,
                )
            }

            // 평소에는 계정 분리 안내를 두고, 실패했을 때 그 자리를 오류 문구가 대신한다.
            // 사용자가 스스로 취소한 경우에는 보여 줄 문구가 없으므로 안내가 그대로 남는다.
            val errorRes = state.error?.messageResOrNull()
            CenteredMessage(
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.component),
                text = stringResource(errorRes ?: R.string.login_provider_conflict),
                style = ManyakTheme.typography.bodySmall,
                color = if (errorRes == null) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDanger,
            )

            ProviderButtons(state = state, onSignIn = onSignIn)

            LegalConsent(
                modifier = Modifier.padding(top = ManyakTheme.spacing.component),
                onOpenLegalDocument = onOpenLegalDocument,
            )

            Spacer(modifier = Modifier.height(ManyakTheme.spacing.block))
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = text,
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
            containerColor = ManyakTheme.colors.backgroundNeutral,
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
        modifier = Modifier.fillMaxWidth().height(ManyakTheme.sizes.control),
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
            // 라벨과 같은 색이면 스피너가 글자보다 무겁게 보인다. 같은 색을 옅게 써서 톤을 맞춘다.
            CircularProgressIndicator(
                modifier = Modifier.size(ManyakTheme.sizes.icon),
                color = contentColor.copy(alpha = PROGRESS_ALPHA),
                strokeWidth = ProgressStrokeWidth,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = null,
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                )
                Text(text = stringResource(labelRes), style = ManyakTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 로그인 버튼 클릭을 묵시적 동의로 간주하는 고지.
 *
 * 두 문서 이름에만 밑줄과 링크를 붙인다 — 문장을 쪼개 별도 링크를 두면 번역·개정 때 문장이 어긋난다.
 */
@Composable
private fun LegalConsent(
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val termsLabel = stringResource(R.string.login_consent_terms)
    val privacyLabel = stringResource(R.string.login_consent_privacy)
    val sentence = stringResource(R.string.login_consent, termsLabel, privacyLabel)
    val linkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

    val annotated =
        buildAnnotatedString {
            append(sentence)
            val links =
                listOf(
                    termsLabel to LegalDocument.TERMS,
                    privacyLabel to LegalDocument.PRIVACY,
                )
            links.forEach { (label, document) ->
                val start = sentence.indexOf(label)
                if (start < 0) return@forEach
                addLink(
                    LinkAnnotation.Clickable(
                        tag = document.name,
                        styles = linkStyles,
                        linkInteractionListener = { onOpenLegalDocument(document) },
                    ),
                    start = start,
                    end = start + label.length,
                )
            }
        }

    Text(
        modifier = modifier.fillMaxWidth(),
        text = annotated,
        style = ManyakTheme.typography.labelSmall,
        color = ManyakTheme.colors.textSubtle,
        textAlign = TextAlign.Center,
    )
}

private const val PROGRESS_ALPHA = 0.55f
private val ProgressStrokeWidth = 2.dp

/** 로고 원본(89×32)의 가로세로 비율. */
private const val LOGO_ASPECT_RATIO = 89f / 32f

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
