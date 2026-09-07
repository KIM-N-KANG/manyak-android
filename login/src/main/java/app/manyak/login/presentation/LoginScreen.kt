package app.manyak.login.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.presentation.error.messageResOrNull
import app.manyak.core.navigation.LegalDocument
import app.manyak.designsystem.component.ManyakLogo
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.login.presentation.component.LoginBackground
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.login.R as LoginR

@Composable
fun LoginScreen(
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 배경 이미지를 전제로 잡은 화면이라 시스템 테마와 무관하게 어두운 팔레트로 고정한다.
    ManyakTheme(darkTheme = true) {
        DarkSystemBars()
        LoginContent(
            state = state,
            onSignIn = { provider -> viewModel.onIntent(LoginIntent.SignIn(provider)) },
            onOpenLegalDocument = onOpenLegalDocument,
            modifier = modifier,
        )
    }
}

/** 화면이 어두우므로 시스템 바 아이콘도 밝은 쪽으로 바꾼다. 화면을 벗어나면 원래대로 돌려놓는다. */
@Composable
private fun DarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = ViewCompat.getWindowInsetsController(view)
        val wasLightStatusBars = controller?.isAppearanceLightStatusBars
        val wasLightNavigationBars = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            wasLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
            wasLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

/**
 * 세로 공간이 모자라면 콘텐츠 전체가 스크롤된다.
 *
 * 배치를 `weight` 스페이서가 아니라 [Arrangement.SpaceBetween] 과 최소 높이로 만든 이유는,
 * 스크롤 컨테이너가 자식에게 무한 높이를 주기 때문이다 — 그 안의 `weight` 는 남는 공간을 나눌 수
 * 없어 0 이 되고 위아래가 붙어 버린다. 최소 높이를 화면 높이로 잡으면 세로가 넉넉할 때의 배치는
 * 그대로 유지되고, 큰 글자나 낮은 화면에서만 늘어나 스크롤이 생긴다.
 */
@Composable
private fun LoginContent(
    state: LoginUiState,
    onSignIn: (AuthProvider) -> Unit,
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(ManyakTheme.colors.surface),
    ) {
        LoginBackground()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // 배경 이미지는 화면 끝까지 두고, 콘텐츠만 시스템 바·컷아웃·키보드를 피한다.
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                val viewportHeight = maxHeight
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = ManyakTheme.spacing.gutter)
                            .heightIn(min = viewportHeight),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LoginHeadline(modifier = Modifier.padding(bottom = ManyakTheme.spacing.block))
                    Column {
                        LoginMessages(state = state)

                        ProviderButtons(state = state, onSignIn = onSignIn)

                        LegalConsent(
                            modifier = Modifier.padding(top = ManyakTheme.spacing.component),
                            onOpenLegalDocument = onOpenLegalDocument,
                        )

                        Spacer(modifier = Modifier.height(ManyakTheme.spacing.block))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginHeadline(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ManyakLogo(modifier = Modifier.padding(top = ManyakTheme.spacing.screenTop))
        Text(
            modifier = Modifier.padding(top = ManyakTheme.spacing.component),
            text = stringResource(LoginR.string.login_headline),
            style = ManyakTheme.typography.titleLarge.copy(shadow = headlineShadow()),
            color = ManyakTheme.colors.text,
        )
    }
}

@Composable
private fun LoginMessages(
    state: LoginUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        state.notice?.messageResOrNull()?.let { noticeRes ->
            CenteredMessage(
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.component),
                text = stringResource(noticeRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textDanger,
            )
        }

        val errorRes = state.error?.messageResOrNull()
        CenteredMessage(
            modifier = Modifier.padding(bottom = ManyakTheme.spacing.component),
            text = stringResource(errorRes ?: LoginR.string.login_provider_conflict),
            style = ManyakTheme.typography.bodySmall,
            color = if (errorRes == null) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDanger,
        )
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
            labelRes = LoginR.string.login_kakao,
            logoRes = DesignsystemR.drawable.ic_logo_kakao,
            containerColor = KakaoContainerColor,
            contentColor = KakaoContentColor,
            state = state,
            onClick = onSignIn,
        )
        ProviderButton(
            provider = AuthProvider.GOOGLE,
            labelRes = LoginR.string.login_google,
            logoRes = DesignsystemR.drawable.ic_logo_google,
            containerColor = GoogleContainerColor,
            contentColor = GoogleContentColor,
            border = BorderStroke(ProviderButtonBorderWidth, ManyakTheme.colors.border),
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
    border: BorderStroke? = null,
) {
    val isBusy = state.inProgress != null
    Button(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .alpha(if (isBusy) DISABLED_BUTTON_ALPHA else 1f),
        onClick = { onClick(provider) },
        enabled = !isBusy,
        shape = ManyakTheme.shapes.control,
        border = border,
        contentPadding =
            PaddingValues(
                horizontal = ManyakTheme.spacing.component,
                vertical = ManyakTheme.spacing.compact,
            ),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor,
            ),
    ) {
        if (state.inProgress == provider) {
            ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon))
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

@Composable
private fun LegalConsent(
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val termsLabel = stringResource(LoginR.string.login_consent_terms)
    val privacyLabel = stringResource(LoginR.string.login_consent_privacy)
    val sentence = stringResource(LoginR.string.login_consent, termsLabel, privacyLabel)
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

@Composable
private fun headlineShadow(): Shadow = Shadow(color = ManyakTheme.colors.surface, blurRadius = HEADLINE_SHADOW_BLUR)

private val ProviderButtonBorderWidth = 1.dp
private const val HEADLINE_SHADOW_BLUR = 12f
private const val DISABLED_BUTTON_ALPHA = 0.75f

private val KakaoContainerColor = Color(0xFFFEE500)
private val KakaoContentColor = Color(0xE6000000)

private val GoogleContainerColor = Color(0xFFF2F2F2)
private val GoogleContentColor = Color(0xFF1F1F1F)
