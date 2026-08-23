package app.manyak.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.manyak.core.domain.session.SessionState
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.navigation.LegalRoute
import app.manyak.core.navigation.LoginRoute
import app.manyak.core.navigation.MyRoute
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.legal.LegalDocumentScreen
import app.manyak.feature.login.LoginScreen
import app.manyak.feature.my.MyScreen

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
        when (sessionState) {
            SessionState.Undetermined -> if (showSessionProgress) SessionProgress()
            is SessionState.SignedOut -> AuthNavHost()
            SessionState.Member -> MainNavHost()
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
 * 인증 그래프. 로그인 성공 시 세션 상태가 바뀌며 이 그래프가 통째로 사라지므로,
 * 메인에서 뒤로가기로 로그인 화면에 돌아갈 수 없다.
 */
@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(onOpenLegalDocument = { document -> navController.openLegalDocument(document) })
        }
        legalDestination(onLeaveDocument = { navController.popBackStack() })
    }
}

/** 메인 그래프. 지금은 마이 화면만 있고 홈·채팅 탭은 이후 범위다. */
@Composable
private fun MainNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MyRoute) {
        composable<MyRoute> { MyScreen() }
        legalDestination(onLeaveDocument = { navController.popBackStack() })
    }
}

/**
 * 공용 법적 문서. 제품 그래프 밖의 목적지로 양쪽 그래프에 등록해, 뒤로가기가 진입한 화면으로 돌아간다.
 * 여기서 임의의 메인 목적지로 이동하는 경로는 두지 않는다.
 */
private fun androidx.navigation.NavGraphBuilder.legalDestination(onLeaveDocument: () -> Unit) {
    composable<LegalRoute> { LegalDocumentScreen(onLeaveDocument = onLeaveDocument) }
}

private fun NavHostController.openLegalDocument(document: LegalDocument) {
    navigate(LegalRoute(document))
}
