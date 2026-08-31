package app.manyak.feature.my

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.settings.ThemeMode
import app.manyak.core.domain.user.AccountStatus
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

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
    modifier: Modifier = Modifier,
    // 내역 화면이 아직 없어 기본값을 둔다. 화면이 생기면 앱 내비게이션이 넘긴다.
    onOpenCreditHistory: () -> Unit = {},
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    // 문구는 구성에 묶인 값이라 컴포지션에서 읽는다. 보상 금액은 효과가 도착할 때 정해져 서식만 나중에 채운다.
    val attendanceClaimed = stringResource(R.string.my_attendance_claimed)
    val attendanceAlready = stringResource(R.string.my_attendance_already)
    val attendanceFailed = stringResource(R.string.my_attendance_failed)
    val linkSucceeded = stringResource(R.string.my_link_succeeded)
    val linkAlreadyLinked = stringResource(R.string.my_link_already_linked)
    val linkFailed = stringResource(R.string.my_link_failed)
    val providerLabels =
        mapOf(
            AuthProvider.GOOGLE to stringResource(R.string.my_provider_google),
            AuthProvider.KAKAO to stringResource(R.string.my_provider_kakao),
        )

    // 잔액·출석 여부는 채팅·제작에서 바뀌므로 화면이 다시 보일 때마다 새로 읽는다. ViewModel 은 탭을
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
                        is MyEffect.AttendanceRewarded -> attendanceClaimed.format(effect.amount)
                        MyEffect.AttendanceAlreadyDone -> attendanceAlready
                        MyEffect.AttendanceFailed -> attendanceFailed
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
        onOpenCreditHistory = onOpenCreditHistory,
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
    onOpenCreditHistory: () -> Unit,
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
        CreditBalanceCard(
            profile = state.profile,
            isClaiming = state.isClaimingAttendance,
            onClaimAttendance = { onIntent(MyIntent.ClaimAttendance) },
            onOpenHistory = onOpenCreditHistory,
        )
        MySection(labelRes = R.string.my_section_event) {
            MyMenuItem(
                iconRes = R.drawable.ic_people,
                labelRes = R.string.my_invite,
                onClick = onOpenInvite,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_chevron_right) },
            )
        }
        MySection(labelRes = R.string.my_section_display) {
            ThemeMenuItem(themeMode = state.themeMode, onClick = { onIntent(MyIntent.CycleTheme) })
        }
        MySection(labelRes = R.string.my_section_etc) {
            MyMenuItem(
                iconRes = R.drawable.ic_mailbox,
                labelRes = R.string.my_feedback,
                onClick = onOpenFeedback,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_chevron_right) },
            )
            MyMenuItem(
                iconRes = R.drawable.ic_info,
                labelRes = R.string.my_service_info,
                onClick = onOpenServiceInfo,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_external_link) },
            )
            MyMenuItem(
                iconRes = R.drawable.ic_book_open,
                labelRes = R.string.my_open_source_license,
                onClick = onOpenOpenSourceLicense,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_chevron_right) },
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
            iconRes = R.drawable.ic_logout,
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
            iconRes = R.drawable.ic_user_x,
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
        iconRes = R.drawable.ic_programming,
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
            ThemeMode.SYSTEM -> R.drawable.ic_display to R.string.my_theme_system
            ThemeMode.LIGHT -> R.drawable.ic_sun to R.string.my_theme_light
            ThemeMode.DARK -> R.drawable.ic_moon to R.string.my_theme_dark
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

/** [onClick] 이 없으면 값만 보여 주는 행이다 — 버전처럼 열 곳이 없는 항목이 여기 해당한다. */
@Composable
@Suppress("LongParameterList")
private fun MyMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = ManyakTheme.colors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable =
        if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onClick)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .then(clickable)
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(MenuIconSize),
            tint = contentColor,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(labelRes),
            style = ManyakTheme.typography.bodyLarge,
            color = contentColor,
        )
        trailing?.invoke()
    }
}

/** 목적지 이동·바깥 문서 열림을 알리는 오른쪽 끝 표시. */
@Composable
private fun MenuTrailingIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier.size(ManyakTheme.sizes.iconSmall),
        tint = ManyakTheme.colors.textSubtlest,
    )
}

/** 메뉴 항목의 왼쪽 아이콘. 라벨 옆이지만 목록의 주된 시각 요소라 [ManyakTheme.sizes.icon]보다 크다. */
private val MenuIconSize = 24.dp

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
            onOpenCreditHistory = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}
