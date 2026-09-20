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
import app.manyak.legal.consent.presentation.LegalConsentSheet
import app.manyak.legal.consent.presentation.LegalConsentViewModel
import app.manyak.my.invite.presentation.onboarding.InviteOnboardingSheet
import app.manyak.notification.consent.presentation.MarketingConsentIntent
import app.manyak.notification.consent.presentation.MarketingConsentSheet
import app.manyak.notification.consent.presentation.MarketingConsentViewModel
import app.manyak.notification.presentation.NotificationPermissionRequest

/**
 * 회원 그래프 위에 얹는 안내의 순서. 알림 권한 → 약관 동의(선택 항목으로 광고 알림) → 초대 코드 → 광고 재질문이다.
 * 권한을 먼저 묻는 이유는 시트의 선택 항목이 기기 알림 상태를 보고 실리기 때문이고, 약관 동의가 끝나야 초대 코드·
 * 재질문이 시작돼 시트 위에 다른 창이 겹치지 않는다.
 *
 * 두 ViewModel 은 여기서 만들어 시트에 넘긴다. 약관 시트의 선택 항목(광고 알림) 답은 알림 기능이 저장·통지하므로,
 * 루트가 답을 넘기고 소비 표시를 남겨 재구성에서 두 번 넘기지 않는다.
 */
@Composable
internal fun MemberOverlays(viewModel: RootViewModel) {
    val consentViewModel: LegalConsentViewModel = hiltViewModel()
    val consentState by consentViewModel.uiState.collectAsStateWithLifecycle()
    val marketingViewModel: MarketingConsentViewModel = hiltViewModel()
    val marketingState by marketingViewModel.uiState.collectAsStateWithLifecycle()

    // 알림 권한은 설치당 한 번, 회원 그래프가 처음 그려질 때 묻는다. 거부해도 아무것도 바뀌지 않는다.
    var permissionSettled by rememberSaveable { mutableStateOf(false) }
    NotificationPermissionRequest(onSettled = { permissionSettled = true })

    LegalConsentSheet(enabled = permissionSettled, viewModel = consentViewModel)
    LaunchedEffect(consentState.marketingAnswer) {
        val answer = consentState.marketingAnswer ?: return@LaunchedEffect
        marketingViewModel.onIntent(MarketingConsentIntent.AnsweredInConsentSheet(answer))
        consentViewModel.onIntent(LegalConsentIntent.MarketingAnswerConsumed)
    }

    // 광고 동의 저장·통지가 도는 동안은 다음 안내를 시작하지 않는다 — 통지 다이얼로그 위에 다른 창이 겹치지 않게.
    val onboardingReady =
        permissionSettled && consentState.isSatisfied && consentState.marketingAnswer == null && !marketingState.isBusy
    if (onboardingReady) {
        // 신규 가입 안내는 어느 탭에 있든 회원 그래프 위에 뜬다. 로그인 화면에 두면
        // 로그인 성공과 동시에 인증 백스택이 사라져 안내도 함께 걷힌다.
        InviteOnboardingSheet()
    }
    // 광고 동의 재질문은 초대 코드 안내가 끝난 뒤에 한다 — 다른 시트 위에 겹쳐 뜨면 무엇에 답하는지 흐려진다.
    // 통지 다이얼로그를 그리므로 항상 조합해 둔다.
    val invitePending by viewModel.inviteOnboardingPending.collectAsStateWithLifecycle()
    MarketingConsentSheet(enabled = onboardingReady && !invitePending, viewModel = marketingViewModel)
}
