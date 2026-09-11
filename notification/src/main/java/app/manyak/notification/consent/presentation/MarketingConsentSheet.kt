package app.manyak.notification.consent.presentation

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notification.settings.entity.PushSettings
import app.manyak.notification.R as NotificationR

/**
 * 첫 진입에서 광고 알림 수신 동의를 묻는 시트. 회원 그래프 위에 얹는다.
 *
 * OS 알림 권한과 별개의 동의라 시트를 따로 띄운다. 야간 수신은 별도 동의라 시트에서 받지 않고 알림 설정에 맡긴다.
 * 전송자·이용 항목 같은 고지는 개인정보 처리방침이 정본이라 시트에는 싣지 않는다 — 설정 화면의 광고 행이 문서를 연다.
 * 기기 알림이 꺼져 있으면 묻지 않고 물었다는 기록도 남기지 않는다 — 동의해도 표시되지 않으니 켜진 뒤 첫
 * 실행에서 묻는 편이 낫다.
 *
 * @param enabled 앞선 안내(알림 권한 응답·초대 코드 시트)가 끝났는가. 참이 되는 순간 한 번 판정한다.
 */
@Composable
fun MarketingConsentSheet(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MarketingConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(viewModel, enabled) {
        if (enabled && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            viewModel.onIntent(MarketingConsentIntent.Prepare)
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    MarketingConsentEffect.SaveFailed -> {
                        val messageRes = NotificationR.string.notification_settings_save_failed
                        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (state.isSheetVisible) {
        MarketingConsentContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
    }
    state.notice?.let { notice ->
        ConsentNoticeDialog(
            notice = notice,
            onDismiss = { viewModel.onIntent(MarketingConsentIntent.DismissNotice) },
        )
    }
}

@Composable
private fun MarketingConsentContent(
    state: MarketingConsentUiState,
    onIntent: (MarketingConsentIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decline = { onIntent(MarketingConsentIntent.Decline) }
    ManyakBottomSheet(
        modifier = modifier,
        // 스크림 탭·끌어내리기·뒤로가기는 "닫기"와 같은 처리다.
        onDismissRequest = { if (!state.isSubmitting) decline() },
        // 저장 중에는 닫히지 않는다 — 결과가 나오기 전에 사라지면 동의가 됐는지 알 수 없다.
        dismissEnabled = !state.isSubmitting,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact)) {
            Text(
                text = stringResource(NotificationR.string.notification_consent_title),
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
            )
            Text(
                text = stringResource(NotificationR.string.notification_consent_description),
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        MarketingConsentActions(
            isSubmitting = state.isSubmitting,
            onDecline = decline,
            onAccept = { onIntent(MarketingConsentIntent.Accept) },
        )
    }
}

/** 초대 코드 안내와 같은 배치 — 주 동작은 전체 폭, 닫기는 그 아래 한 단 작게. */
@Composable
private fun MarketingConsentActions(
    isSubmitting: Boolean,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = ManyakTheme.sizes.control),
            onClick = onAccept,
            enabled = !isSubmitting,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                    disabledContainerColor = ManyakTheme.colors.brand,
                    disabledContentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
                Text(
                    modifier = Modifier.alpha(if (isSubmitting) 0f else 1f),
                    text = stringResource(NotificationR.string.notification_consent_accept),
                    style = ManyakTheme.typography.labelLarge,
                )
                if (isSubmitting) {
                    ManyakProgressIndicator(
                        modifier = Modifier.size(ManyakTheme.sizes.icon),
                        color = ManyakTheme.colors.textInverse,
                    )
                }
            }
        }
        // M3 `TextButton` 은 최소 터치 타깃 48dp 를 레이아웃 높이로 밀어 올려 32dp 가 나오지 않는다.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ManyakTheme.sizes.controlSmall)
                    .clip(ManyakTheme.shapes.control)
                    .clickable(enabled = !isSubmitting, role = Role.Button, onClick = onDecline),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(NotificationR.string.notification_consent_decline),
                style = ManyakTheme.typography.labelSmall,
                color = if (isSubmitting) ManyakTheme.colors.textDisabled else ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Preview(name = "광고 알림 동의 · 라이트")
@Composable
private fun MarketingConsentSheetPreview() {
    ManyakTheme(darkTheme = false) {
        MarketingConsentContent(
            state =
                MarketingConsentUiState(
                    settings = PushSettings(servicePush = true, marketingPush = false, marketingNightPush = false),
                ),
            onIntent = {},
        )
    }
}
