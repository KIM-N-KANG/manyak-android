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
    onOpenWithdrawal: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

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
                        is MyEffect.AttendanceRewarded ->
                            context.getString(R.string.my_attendance_claimed, effect.amount)

                        MyEffect.AttendanceAlreadyDone -> context.getString(R.string.my_attendance_already)
                        MyEffect.AttendanceFailed -> context.getString(R.string.my_attendance_failed)
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
        onOpenWithdrawal = onOpenWithdrawal,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
@Suppress("LongParameterList")
private fun MyContent(
    state: MyUiState,
    onIntent: (MyIntent) -> Unit,
    onOpenInvite: () -> Unit,
    onOpenServiceInfo: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenWithdrawal: () -> Unit,
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
        ProfileHeader(profile = state.profile)
        CreditBalanceCard(
            profile = state.profile,
            isClaiming = state.isClaimingAttendance,
            onClaimAttendance = { onIntent(MyIntent.ClaimAttendance) },
        )
        MySection(labelRes = R.string.my_section_event) {
            MyMenuItem(
                iconRes = R.drawable.ic_people,
                labelRes = R.string.my_invite,
                onClick = onOpenInvite,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_angle_right) },
            )
        }
        MySection(labelRes = R.string.my_section_display) {
            ThemeMenuItem(themeMode = state.themeMode, onClick = { onIntent(MyIntent.CycleTheme) })
        }
        MySection(labelRes = R.string.my_section_etc) {
            MyMenuItem(
                iconRes = R.drawable.ic_info,
                labelRes = R.string.my_service_info,
                onClick = onOpenServiceInfo,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_external_link) },
            )
            MyMenuItem(
                iconRes = R.drawable.ic_mailbox,
                labelRes = R.string.my_feedback,
                onClick = onOpenFeedback,
                trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_angle_right) },
            )
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

@Composable
@Suppress("LongParameterList")
private fun MyMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = ManyakTheme.colors.text,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .clickable(enabled = enabled, onClick = onClick)
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
            onOpenWithdrawal = {},
            contentPadding = PaddingValues(0.dp),
        )
    }
}
