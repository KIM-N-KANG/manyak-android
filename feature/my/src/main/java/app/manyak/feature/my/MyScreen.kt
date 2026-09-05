package app.manyak.feature.my

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.presentation.LocalAnalytics
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.settings.ThemeMode
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.core.ui.R
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 마이 탭. 화면 제목은 셸 헤더가 표시하므로 여기서 다시 그리지 않는다.
 */
@Composable
fun MyScreen(
    contentPadding: PaddingValues,
    onOpenInvite: () -> Unit,
    onOpenServiceInfo: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
    onOpenWithdrawal: () -> Unit,
    onOpenCreditCharge: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    // 문구는 구성에 묶인 값이라 컴포지션에서 읽는다.
    val linkSucceeded = stringResource(R.string.my_link_succeeded)
    val linkAlreadyLinked = stringResource(R.string.my_link_already_linked)
    val linkFailed = stringResource(R.string.my_link_failed)
    val providerLabels =
        mapOf(
            AuthProvider.GOOGLE to stringResource(R.string.my_provider_google),
            AuthProvider.KAKAO to stringResource(R.string.my_provider_kakao),
        )

    // 잔액은 채팅·제작·이프 충전에서 바뀌므로 화면이 다시 보일 때마다 새로 읽는다. ViewModel 은 탭을
    // 옮겨도 살아 있어(탭별 백스택이 목적지를 계속 들고 있다) 생성 시점 한 번으로는 낡은 값이 남는다.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onIntent(MyIntent.Refresh)
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                val message =
                    when (effect) {
                        is MyEffect.AccountLinked ->
                            linkSucceeded.format(providerLabels[effect.provider].orEmpty())
                        MyEffect.AccountAlreadyLinked -> linkAlreadyLinked
                        MyEffect.AccountLinkFailed -> linkFailed
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    MyContent(
        state = state,
        onIntent = viewModel::onIntent,
        onOpenInvite = onOpenInvite,
        onOpenServiceInfo = onOpenServiceInfo,
        onOpenFeedback = onOpenFeedback,
        onOpenOpenSourceLicense = onOpenOpenSourceLicense,
        onOpenWithdrawal = onOpenWithdrawal,
        onOpenCreditCharge = onOpenCreditCharge,
        contentPadding = contentPadding,
        modifier = modifier,
    )

    // 다이얼로그는 자체 창에 뜨므로 스크롤 본문 밖에서 연다.
    AccountLinkDialogs(state = state, onIntent = viewModel::onIntent)
}

/**
 * 확인 다이얼로그는 이미 연동된 제공자를 알아야 열 수 있다 — 재인증이 그 제공자로 진행된다는 예고가
 * 문구의 핵심이라서다.
 */
@Composable
private fun AccountLinkDialogs(
    state: MyUiState,
    onIntent: (MyIntent) -> Unit,
) {
    val current = state.profile?.linkedProviders?.firstOrNull()
    val target = state.accountLinkTarget
    if (current != null && target != null) {
        AccountLinkConfirmDialog(
            current = current,
            target = target,
            inProgress = state.isLinkingAccount,
            onConfirm = { onIntent(MyIntent.ConfirmAccountLink) },
            onDismiss = { onIntent(MyIntent.DismissAccountLink) },
        )
    }
    if (state.showsLinkedToOtherUserNotice) {
        LinkedToOtherUserDialog(onDismiss = { onIntent(MyIntent.DismissLinkedToOtherUserNotice) })
    }
}

@Composable
@Suppress("LongParameterList")
private fun MyContent(
    state: MyUiState,
    onIntent: (MyIntent) -> Unit,
    onOpenInvite: () -> Unit,
    onOpenServiceInfo: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
    onOpenWithdrawal: () -> Unit,
    onOpenCreditCharge: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
    ) {
        ProfileHeader(
            profile = state.profile,
            onLinkAccount = { provider -> onIntent(MyIntent.RequestAccountLink(provider)) },
        )
        val analytics = LocalAnalytics.current
        CreditBalanceCard(
            profile = state.profile,
            onOpenCharge = {
                analytics.track(AnalyticsEvent.CreditChargeButtonClicked)
                onOpenCreditCharge()
            },
        )
        MySection(labelRes = R.string.my_section_event) {
            InviteMenuItem(onClick = onOpenInvite)
        }
        MySection(labelRes = R.string.my_section_display) {
            ThemeMenuItem(themeMode = state.themeMode, onClick = { onIntent(MyIntent.CycleTheme) })
        }
        MySection(labelRes = R.string.my_section_etc) {
            MyMenuItem(
                iconRes = DesignsystemR.drawable.ic_mailbox,
                labelRes = R.string.my_feedback,
                onClick = onOpenFeedback,
                trailing = { MenuTrailingIcon(iconRes = DesignsystemR.drawable.ic_chevron_right) },
            )
            MyMenuItem(
                iconRes = DesignsystemR.drawable.ic_info,
                labelRes = CommonR.string.my_service_info,
                onClick = onOpenServiceInfo,
                trailing = { MenuTrailingIcon(iconRes = DesignsystemR.drawable.ic_external_link) },
            )
            MyMenuItem(
                iconRes = DesignsystemR.drawable.ic_book_open,
                labelRes = R.string.my_open_source_license,
                onClick = onOpenOpenSourceLicense,
                trailing = { MenuTrailingIcon(iconRes = DesignsystemR.drawable.ic_chevron_right) },
            )
            AppVersionMenuItem()
        }
        AccountSection(state = state, onIntent = onIntent, onOpenWithdrawal = onOpenWithdrawal)
    }
}

@Composable
private fun AccountSection(
    state: MyUiState,
    onIntent: (MyIntent) -> Unit,
    onOpenWithdrawal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MySection(labelRes = R.string.my_section_account, modifier = modifier) {
        MyMenuItem(
            iconRes = DesignsystemR.drawable.ic_logout,
            labelRes = if (state.isLoggingOut) R.string.my_logout_in_progress else R.string.my_logout,
            onClick = { onIntent(MyIntent.LogOut) },
            enabled = !state.isLoggingOut,
            trailing =
                if (state.isLoggingOut) {
                    { ManyakProgressIndicator(modifier = Modifier.size(ManyakTheme.sizes.icon)) }
                } else {
                    null
                },
        )
        MyMenuItem(
            iconRes = DesignsystemR.drawable.ic_user_x,
            labelRes = R.string.my_withdrawal,
            onClick = onOpenWithdrawal,
            contentColor = ManyakTheme.colors.textDanger,
        )
    }
}

@Composable
private fun MySection(
    @StringRes labelRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = ManyakTheme.spacing.gutter),
    ) {
        Text(
            modifier =
                Modifier
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    .padding(bottom = ManyakTheme.spacing.compact),
            text = stringResource(labelRes),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.text,
        )
        content()
    }
}

@Composable
private fun AppVersionMenuItem(modifier: Modifier = Modifier) {
    MyMenuItem(
        iconRes = DesignsystemR.drawable.ic_programming,
        labelRes = R.string.my_app_version,
        onClick = null,
        modifier = modifier,
        trailing = {
            Text(
                text = rememberAppVersionName(),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
    )
}

/** 라이브러리 모듈의 `BuildConfig` 에는 버전이 없어 설치된 패키지 정보에서 읽는다. */
@Composable
private fun rememberAppVersionName(): String {
    val context = LocalContext.current
    val unknown = stringResource(R.string.my_app_version_unknown)
    return remember(context, unknown) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            ?: unknown
    }
}

@Composable
private fun ThemeMenuItem(
    themeMode: ThemeMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (iconRes, descriptionRes) =
        when (themeMode) {
            ThemeMode.SYSTEM -> DesignsystemR.drawable.ic_display to R.string.my_theme_system
            ThemeMode.LIGHT -> DesignsystemR.drawable.ic_sun to R.string.my_theme_light
            ThemeMode.DARK -> DesignsystemR.drawable.ic_moon to R.string.my_theme_dark
        }
    MyMenuItem(
        iconRes = iconRes,
        labelRes = R.string.my_theme,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        },
    )
}

@Preview(showBackground = true, name = "마이 · 라이트")
@Composable
private fun MyScreenPreview() {
    ManyakTheme(darkTheme = false) {
        MyContent(
            state =
                MyUiState(
                    profile =
                        UserProfile(
                            id = "user-1",
                            nickname = "낭만적인 표류자",
                            profileImageUrl = null,
                            profileThumbnailBase64 = null,
                            status = AccountStatus.ACTIVE,
                            creditBalance = 1130,
                            attendedToday = true,
                            linkedProviders = listOf(AuthProvider.GOOGLE),
                        ),
                ),
            onIntent = {},
            onOpenInvite = {},
            onOpenServiceInfo = {},
            onOpenFeedback = {},
            onOpenOpenSourceLicense = {},
            onOpenWithdrawal = {},
            onOpenCreditCharge = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}
