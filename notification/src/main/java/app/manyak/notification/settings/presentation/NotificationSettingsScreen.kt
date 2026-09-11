package app.manyak.notification.settings.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.domain.error.DomainError
import app.manyak.common.presentation.error.messageResOrNull
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notification.consent.presentation.ConsentNoticeDialog
import app.manyak.notification.settings.entity.PushSettings
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.notification.R as NotificationR

/**
 * 알림 수신 동의 설정. 토글은 즉시 저장되고 실패하면 되돌아온다.
 * 기기 알림이 꺼져 있으면 토글 위 배너가 시스템 설정으로 보낸다 — 앱 안에서 권한을 다시 묻지 않는다.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) { viewModel.onIntent(NotificationSettingsIntent.Load) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    NotificationSettingsEffect.SaveFailed -> {
                        val messageRes = NotificationR.string.notification_settings_save_failed
                        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        NotificationSettingsHeader(onBack = onBack)
        NotificationSettingsContent(
            state = state,
            onIntent = viewModel::onIntent,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            modifier = Modifier.weight(1f),
        )
    }
    state.notice?.let { notice ->
        ConsentNoticeDialog(
            notice = notice,
            onDismiss = { viewModel.onIntent(NotificationSettingsIntent.DismissNotice) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(NotificationR.string.notification_settings_title),
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
        },
        navigationIcon = {
            ManyakIconButton(
                iconRes = DesignsystemR.drawable.ic_arrow_back,
                contentDescription = stringResource(CommonR.string.common_back),
                onClick = onBack,
            )
        },
        // 화면 루트에서 적용한 safeDrawing 인셋이 중복되지 않게 한다.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}

@Composable
private fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onIntent: (NotificationSettingsIntent) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadError = state.loadError
    if (loadError != null) {
        LoadFailedContent(
            message = stringResource(loadError.loadFailedMessageRes()),
            onRetry = { onIntent(NotificationSettingsIntent.Retry) },
            modifier = modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
        )
        return
    }
    val settings = state.settings
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // 제작 탭 목록과 같은 리듬 — 배너는 헤더 바로 아래 붙고, 끝에만 여유를 둔다.
                .padding(bottom = ManyakTheme.spacing.compact),
    ) {
        NotificationsDisabledBanner(
            // 행이 위쪽 여백 12dp 를 스스로 갖고 있어, 4dp 를 더하면 제작 탭처럼 배너와 첫 항목 사이가 gutter 가 된다.
            modifier =
                Modifier
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    .padding(bottom = ManyakTheme.spacing.inline),
        )
        SettingRow(
            labelRes = NotificationR.string.notification_settings_service,
            descriptionRes = NotificationR.string.notification_settings_service_description,
            checked = settings?.servicePush,
            onToggle = { onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.SERVICE)) },
        )
        SettingRow(
            labelRes = NotificationR.string.notification_settings_marketing,
            descriptionRes = NotificationR.string.notification_settings_marketing_description,
            checked = settings?.marketingPush,
            onToggle = { onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING)) },
            // 동의 고지의 정본은 개인정보 처리방침이라 토글 옆에서 바로 열 수 있게 한다.
            onOpenDetail = onOpenPrivacyPolicy,
        )
        // 야간은 광고 동의의 확장이라 광고가 꺼져 있으면 줄 자체를 두지 않는다 — 켤 수 없는 스위치를 보여 주지 않는다.
        if (settings?.marketingPush == true) {
            SettingRow(
                labelRes = NotificationR.string.notification_settings_marketing_night,
                descriptionRes = NotificationR.string.notification_settings_marketing_night_description,
                checked = settings.marketingNightPush,
                onToggle = { onIntent(NotificationSettingsIntent.Toggle(PushSettingKind.MARKETING_NIGHT)) },
            )
        }
    }
}

/** 정지 계정만 공통 정지 문구를 쓴다. 그 밖의 실패는 이 화면의 일이라 화면 문구로 말한다. */
@StringRes
private fun DomainError.loadFailedMessageRes(): Int =
    if (this == DomainError.AccountSuspended) {
        messageResOrNull() ?: NotificationR.string.notification_settings_load_failed
    } else {
        NotificationR.string.notification_settings_load_failed
    }

/**
 * 토글 한 줄. 스위치만 누르는 대상이고 줄은 눌리지 않는다 — 라벨 옆에 문서를 여는 아이콘이 있어 줄 전체를
 * 토글로 두면 두 눌림이 한 줄에 겹친다. 접근성 이름은 스위치에 라벨을 붙여 읽힌다.
 *
 * @param checked 아직 불러오지 않았으면 null 이고, 라벨은 그대로 둔 채 스위치 자리에만 골격을 깐다.
 * @param onOpenDetail 라벨 옆 외부 링크 아이콘. 줄 토글과 별개의 눌림 대상이라 아이콘 버튼으로 둔다.
 */
@Composable
private fun SettingRow(
    @StringRes labelRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDetail: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                // 위아래 12dp 씩이라 행 사이가 24dp 로 읽힌다.
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.component),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        val label = stringResource(labelRes)
        // 라벨·설명 사이는 마이 메뉴 줄과 같이 줄 높이에만 맡긴다 — 두 화면의 보조 문구 간격이 갈리지 않게 한다.
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                Text(
                    text = label,
                    style = ManyakTheme.typography.bodyLarge,
                    color = ManyakTheme.colors.text,
                )
                if (onOpenDetail != null) {
                    ManyakIconButton(
                        iconRes = DesignsystemR.drawable.ic_external_link,
                        contentDescription =
                            stringResource(NotificationR.string.notification_settings_marketing_privacy_policy),
                        onClick = onOpenDetail,
                        size = ManyakTheme.sizes.controlSmall,
                        iconSize = ManyakTheme.sizes.iconSmall,
                        shape = ManyakTheme.shapes.menuItem,
                        tint = ManyakTheme.colors.textSubtle,
                    )
                }
            }
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodySmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        if (checked == null) {
            SkeletonPlaceholder(
                alpha = rememberSkeletonPulseAlpha(),
                modifier = Modifier.size(width = SwitchTrackWidth, height = SwitchTrackHeight),
            )
        } else {
            PushSwitch(
                modifier = Modifier.semantics { contentDescription = label },
                checked = checked,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

/** 앱 색을 얹은 M3 스위치. 조작은 스위치 자신이 받는다. */
@Composable
private fun PushSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = ManyakTheme.colors.textInverse,
                checkedTrackColor = ManyakTheme.colors.brand,
                uncheckedThumbColor = ManyakTheme.colors.textSubtlest,
                uncheckedTrackColor = ManyakTheme.colors.backgroundNeutral,
                uncheckedBorderColor = ManyakTheme.colors.borderStrong,
            ),
    )
}

/**
 * 기기 알림이 꺼져 있을 때의 안내 배너. 제작 탭의 이어서 만들기 배너와 같은 모양이고, 버튼만 강조색 대신
 * 본문 색이다 — 켜라고 재촉하는 자리가 아니라 지금 상태를 알리는 자리라서다.
 * `areNotificationsEnabled` 하나로 Android 13+ 권한 거부와 그 이하의 앱 알림 끔을 함께 본다.
 *
 * 버튼은 시스템 권한 다이얼로그를 먼저 띄운다. 두 번 거부해 시스템이 더 묻지 않는 상태에서는 다이얼로그 없이
 * 거부 결과만 바로 돌아오므로, 그때는 앱 알림 설정 화면으로 보낸다. 런타임 권한이 없는 API 32 이하는 바로
 * 설정으로 간다. 설정에서 돌아오면 다시 판정해 사라진다.
 */
@Composable
private fun NotificationsDisabledBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var notificationsEnabled by remember { mutableStateOf(context.areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = context.areNotificationsEnabled()
    }
    // 요청 직전의 "다시 물을 수 있음" 판정. 거부 뒤에도 그대로 거짓이면 시스템이 다이얼로그를 띄우지 않은 것이다.
    var couldAskBefore by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> notificationsEnabled = true
                !couldAskBefore && activity?.canAskNotificationPermission() == false ->
                    context.startActivity(appNotificationSettingsIntent(context.packageName))
            }
        }
    if (notificationsEnabled) return
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = ManyakTheme.colors.backgroundNeutral, shape = ManyakTheme.shapes.card)
                .padding(start = ManyakTheme.spacing.gutter, end = ManyakTheme.spacing.inline)
                .padding(vertical = ManyakTheme.spacing.dense),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(NotificationR.string.notification_settings_disabled_notice),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
        ManyakTextButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    couldAskBefore = activity?.canAskNotificationPermission() == true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(appNotificationSettingsIntent(context.packageName))
                }
            },
        ) {
            Text(
                text = stringResource(NotificationR.string.notification_settings_enable),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.text,
            )
        }
    }
}

private fun android.app.Activity.canAskNotificationPermission(): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)

private fun Context.areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/** 앱 알림 설정 화면. 전용 액션은 API 26 부터라 그 아래는 앱 정보 화면으로 보낸다. */
private fun appNotificationSettingsIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }

/** M3 `Switch` 트랙 크기. 골격이 같은 자리를 차지하게 한다. */
private val SwitchTrackWidth = 52.dp
private val SwitchTrackHeight = 32.dp

@Preview(showBackground = true, name = "알림 설정 · 라이트")
@Composable
private fun NotificationSettingsPreview() {
    ManyakTheme(darkTheme = false) {
        NotificationSettingsContent(
            state =
                NotificationSettingsUiState(
                    isLoading = false,
                    settings = PushSettings(servicePush = true, marketingPush = false, marketingNightPush = false),
                ),
            onIntent = {},
            onOpenPrivacyPolicy = {},
        )
    }
}
