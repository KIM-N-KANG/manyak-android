package app.manyak.root

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.manyak.core.navigation.ChatListRoute
import app.manyak.core.navigation.HomeRoute
import app.manyak.core.navigation.MyRoute
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakNavigationBar
import app.manyak.core.ui.component.ManyakNavigationItem
import app.manyak.core.ui.component.ManyakSectionHeader
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.chat.ChatListScreen
import app.manyak.feature.home.HomeScreen
import app.manyak.feature.my.MyScreen
import kotlin.reflect.KClass

/**
 * 목적지와 탭의 대응을 맺는 유일한 자리.
 *
 * `:core:ui` 는 라우트를 알 수 없고 `:feature:*` 끼리도 서로 모르므로, 둘을 다 아는 `:app` 이 맺는다.
 */
private enum class MainTab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:DrawableRes val selectedIconRes: Int,
    @param:DrawableRes val unselectedIconRes: Int,
    @param:StringRes val nameRes: Int,
) {
    HOME(
        route = HomeRoute,
        routeClass = HomeRoute::class,
        selectedIconRes = R.drawable.ic_nav_home_filled,
        unselectedIconRes = R.drawable.ic_nav_home_outline,
        nameRes = R.string.main_tab_home,
    ),
    CHAT(
        route = ChatListRoute,
        routeClass = ChatListRoute::class,
        selectedIconRes = R.drawable.ic_nav_chat_filled,
        unselectedIconRes = R.drawable.ic_nav_chat_outline,
        nameRes = R.string.main_tab_chat,
    ),
    MY(
        route = MyRoute,
        routeClass = MyRoute::class,
        selectedIconRes = R.drawable.ic_nav_my_filled,
        unselectedIconRes = R.drawable.ic_nav_my_outline,
        nameRes = R.string.main_tab_my,
    ),
}

/**
 * 하단 탭 셋을 두르는 셸. 헤더와 하단 바를 여기서만 그리고, 탭 화면에는 chrome 이 차지한 여백만 넘긴다.
 *
 * 탭 전용 [NavHost] 를 따로 두는 이유는 셸을 두르지 않는 화면(법적 문서 등)이 이 여백을 물려받지 않게
 * 하기 위해서다. 그 화면들은 바깥 그래프에서 이 셸의 형제로 등록한다.
 */
@Composable
fun MainTabsScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab = backStackEntry?.destination.toMainTab() ?: MainTab.HOME

    Scaffold(
        modifier = modifier,
        containerColor = ManyakTheme.colors.surface,
        topBar = { ManyakSectionHeader(titleRes = selectedTab.nameRes) },
        bottomBar = {
            ManyakNavigationBar(
                items =
                    MainTab.entries.map { tab ->
                        ManyakNavigationItem(
                            selectedIconRes = tab.selectedIconRes,
                            unselectedIconRes = tab.unselectedIconRes,
                            nameRes = tab.nameRes,
                            selected = tab == selectedTab,
                            onSelect = { navController.selectTab(tab) },
                        )
                    },
            )
        },
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> { HomeScreen(contentPadding = innerPadding) }
            composable<ChatListRoute> { ChatListScreen(contentPadding = innerPadding) }
            composable<MyRoute> { MyScreen(contentPadding = innerPadding) }
        }
    }
}

/**
 * 탭 전환을 백스택에 쌓지 않는다.
 *
 * 시작 목적지까지 되감되 시작 목적지 자체는 남겨, 홈이 아닌 탭에서 뒤로가기가 홈으로 한 번에
 * 돌아오고 홈에서 앱을 벗어나게 한다. 되감을 때 상태를 저장하고 복귀할 때 복원해야 탭을 오갔다
 * 돌아왔을 때 스크롤 위치가 유지된다.
 */
private fun NavHostController.selectTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.toMainTab(): MainTab? =
    this?.let { destination -> MainTab.entries.firstOrNull { tab -> destination.hasRoute(tab.routeClass) } }
