package app.manyak.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.legal.consent.presentation.LegalConsentIntent
import app.manyak.legal.consent.presentation.LegalConsentViewModel
import app.manyak.my.invite.presentation.onboarding.InviteOnboardingSheet
import app.manyak.notification.consent.presentation.MarketingConsentIntent
import app.manyak.notification.consent.presentation.MarketingConsentSheet
import app.manyak.notification.consent.presentation.MarketingConsentViewModel
import app.manyak.notification.presentation.NotificationPermissionRequest

/** 필수 동의 이후 알림 권한, 선택 동의 처리, 초대 코드, 광고 재질문 순으로 안내한다. */
@Composable
internal fun MemberOverlays(
    viewModel: RootViewModel,
    consentViewModel: LegalConsentViewModel,
) {
    val consentState by consentViewModel.uiState.collectAsStateWithLifecycle()
    val marketingViewModel: MarketingConsentViewModel = hiltViewModel()
    val marketingState by marketingViewModel.uiState.collectAsStateWithLifecycle()

    // 알림 권한은 설치당 한 번, 회원 그래프가 처음 그려질 때 묻는다. 거부해도 아무것도 바뀌지 않는다.
    var permissionSettled by rememberSaveable { mutableStateOf(false) }
    NotificationPermissionRequest(onSettled = { permissionSettled = true })

    LaunchedEffect(permissionSettled, consentState.marketingAnswer) {
        if (!permissionSettled) return@LaunchedEffect
        val answer = consentState.marketingAnswer ?: return@LaunchedEffect
        marketingViewModel.onIntent(MarketingConsentIntent.AnsweredInConsentSheet(answer))
        consentViewModel.onIntent(LegalConsentIntent.MarketingAnswerConsumed)
    }

    // 광고 동의 저장·통지가 도는 동안은 다음 안내를 시작하지 않는다 — 통지 다이얼로그 위에 다른 창이 겹치지 않게.
    val onboardingReady =
        permissionSettled && consentState.isSatisfied && consentState.marketingAnswer == null && !marketingState.isBusy
    if (onboardingReady) {
        // 동의 화면에서 회원 화면으로 옮겨온 뒤에도 가입 안내 기록을 소비한다.
        InviteOnboardingSheet()
    }
    // 광고 동의 재질문은 초대 코드 안내가 끝난 뒤에 한다 — 다른 시트 위에 겹쳐 뜨면 무엇에 답하는지 흐려진다.
    // 통지 다이얼로그를 그리므로 항상 조합해 둔다.
    val invitePending by viewModel.inviteOnboardingPending.collectAsStateWithLifecycle()
    MarketingConsentSheet(enabled = onboardingReady && !invitePending, viewModel = marketingViewModel)
}
