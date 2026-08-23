package app.manyak.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.manyak.core.domain.session.SessionState
import app.manyak.core.navigation.CreateKeywordRoute
import app.manyak.core.navigation.LegalRoute
import app.manyak.core.navigation.LoginRoute
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.error.messageResOrNull
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.create.CreateKeywordScreen
import app.manyak.feature.legal.LegalDocumentScreen
import app.manyak.feature.login.LoginScreen

/**
 * 세션 상태가 어느 그래프를 띄울지 결정한다. 그래프 안에서 가드로 막지 않는다 —
 * 미로그인 상태에서 **메인 목적지가 백스택에 존재할 수 없게** 만들어 가드 누락이 사고가 되지 않게 한다.
 *
 * 상태가 미확정인 동안에는 어느 그래프도 그리지 않는다. 로그인 화면이 잠깐 스쳤다가 메인으로 바뀌는
 * 깜빡임을 막기 위해서다.
 */
@Composable
fun ManyakApp(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val showSessionProgress = rememberDelayedProgressVisibility(sessionState == SessionState.Undetermined)

    Surface(modifier = modifier.fillMaxSize(), color = ManyakTheme.colors.surface) {
        when (val state = sessionState) {
            SessionState.Undetermined -> if (showSessionProgress) SessionProgress()
            is SessionState.SignedOut -> AuthNavDisplay()
            SessionState.Member -> MainNavDisplay()
            // 이전 사용자의 데이터가 남아 있다. 정리가 끝날 때까지 어느 그래프도 열지 않는다.
            is SessionState.CleanupFailed -> CleanupFailed(state, onRetry = viewModel::onRetryCleanup)
        }
    }
}

/**
 * 종료 정리가 재시도를 소진했을 때의 차단 화면.
 *
 * 로그인 화면을 대신 열면 새 계정이 이전 사용자의 잔여 데이터 위에서 시작되고, 남은 저널 때문에
 * 다음 실행의 재개가 그 새 계정의 데이터를 지운다. 그래서 여기서는 다시 시도만 제공한다.
 */
@Composable
private fun CleanupFailed(
    state: SessionState.CleanupFailed,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.notice.messageResOrNull()?.let { noticeRes ->
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(noticeRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.session_cleanup_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textDanger,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
        ) {
            Text(text = stringResource(R.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
}

/**
 * 상태가 미확정인 동안 보여 주는 표시.
 *
 * 로그아웃 중에는 정리가 끝날 때까지 유지된다. 종료 정리는 서버 호출과 저장소 삭제를 포함해 눈에 띄는
 * 시간이 걸리므로, 빈 화면 대신 진행 중임을 알린다. 반대로 앱 시작 직후처럼 상태가 금방 확정되는
 * 경우에는 호출부가 아예 그리지 않는다 — 스피너가 스쳐 가는 깜빡임을 만들지 않는다.
 */
@Composable
private fun SessionProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ManyakProgressIndicator()
    }
}

/**
 * 인증 백스택. 로그인 성공 시 세션 상태가 바뀌며 이 백스택이 통째로 사라지므로,
 * 메인에서 뒤로가기로 로그인 화면에 돌아갈 수 없다.
 */
@Composable
private fun AuthNavDisplay() {
    val backStack = rememberNavBackStack(LoginRoute)
    val screenTransition = rememberScreenTransition()
    NavDisplay(
        backStack = backStack,
        entryDecorators = rememberManyakEntryDecorators(),
        transitionSpec = screenTransition,
        popTransitionSpec = screenTransition,
        entryProvider =
            entryProvider<NavKey> {
                entry<LoginRoute> {
                    LoginScreen(onOpenLegalDocument = { document -> backStack.add(LegalRoute(document)) })
                }
                legalEntry(onLeaveDocument = { backStack.removeLastOrNull() })
            },
    )
}

/**
 * 메인 백스택. 하단 탭 셋은 셸이 소유한 탭별 백스택 안에 있고, 셸을 두르지 않는 화면은
 * 셸 키 위에 쌓여 헤더도 하단 탭도 없이 전체 화면으로 그려진다.
 */
@Composable
private fun MainNavDisplay() {
    val backStack = rememberNavBackStack(MainTabsRoute)
    val screenTransition = rememberScreenTransition()
    NavDisplay(
        backStack = backStack,
        entryDecorators = rememberManyakEntryDecorators(),
        transitionSpec = screenTransition,
        popTransitionSpec = screenTransition,
        entryProvider =
            entryProvider<NavKey> {
                entry<MainTabsRoute> {
                    MainTabsScreen(onCreateStory = { backStack.add(CreateKeywordRoute) })
                }
                // 간편 제작 퍼널은 단계마다 목적지를 두고 다음 단계 이동을 push 로 표현한다.
                // 키워드 단계의 뒤로가기는 이탈 가드 없이 진입 전 화면으로 나간다.
                entry<CreateKeywordRoute> {
                    CreateKeywordScreen(onLeaveFunnel = { backStack.removeLastOrNull() })
                }
                legalEntry(onLeaveDocument = { backStack.removeLastOrNull() })
            },
    )
}

/**
 * 공용 법적 문서. 제품 백스택 양쪽에 등록해 뒤로가기가 진입한 화면으로 돌아간다.
 * 여기서 임의의 메인 목적지로 이동하는 경로는 두지 않는다.
 */
private fun EntryProviderScope<NavKey>.legalEntry(onLeaveDocument: () -> Unit) {
    entry<LegalRoute> { route ->
        LegalDocumentScreen(document = route.document, onLeaveDocument = onLeaveDocument)
    }
}
