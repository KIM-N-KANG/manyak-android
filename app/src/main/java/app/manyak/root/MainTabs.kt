package app.manyak.root

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
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

/**
 * 목적지와 탭의 대응을 맺는 유일한 자리.
 *
 * `:core:ui` 는 라우트를 알 수 없고 `:feature:*` 끼리도 서로 모르므로, 둘을 다 아는 `:app` 이 맺는다.
 */
private enum class MainTab(
    @param:DrawableRes val selectedIconRes: Int,
    @param:DrawableRes val unselectedIconRes: Int,
    @param:StringRes val nameRes: Int,
) {
    HOME(
        selectedIconRes = R.drawable.ic_nav_home_filled,
        unselectedIconRes = R.drawable.ic_nav_home_outline,
        nameRes = R.string.main_tab_home,
    ),
    CHAT(
        selectedIconRes = R.drawable.ic_nav_chat_filled,
        unselectedIconRes = R.drawable.ic_nav_chat_outline,
        nameRes = R.string.main_tab_chat,
    ),
    MY(
        selectedIconRes = R.drawable.ic_nav_my_filled,
        unselectedIconRes = R.drawable.ic_nav_my_outline,
        nameRes = R.string.main_tab_my,
    ),
}

/**
 * 하단 탭 셋을 두르는 셸. 헤더와 하단 바를 여기서만 그리고, 탭 화면에는 chrome 이 차지한 여백만 넘긴다.
 *
 * **탭마다 백스택을 따로 소유한다.** 탭을 옮겨도 떠난 탭의 백스택과 그에 묶인 상태가 남아 있어,
 * 돌아왔을 때 스크롤 위치와 목록 상태가 유지된다. 탭 전환 자체는 어느 백스택을 그릴지 고르는 일이라
 * 이력에 쌓이지 않는다.
 */
@Composable
fun MainTabsScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val backStacks = rememberTabBackStacks()

    Scaffold(
        modifier = modifier,
        containerColor = ManyakTheme.colors.surface,
        topBar = { ManyakSectionHeader(titleRes = selectedTab.nameRes) },
        bottomBar = {
            MainTabsBar(
                selectedTab = selectedTab,
                // 이미 선택된 탭을 다시 누르면 그 탭의 시작 목적지로 되돌린다.
                onSelectTab = { tab ->
                    if (tab == selectedTab) backStacks.getValue(tab).popToStart() else selectedTab = tab
                },
            )
        },
    ) { innerPadding ->
        MainTabsContent(
            selectedTab = selectedTab,
            backStacks = backStacks,
            contentPadding = innerPadding,
            onLeaveTab = { selectedTab = MainTab.HOME },
        )
    }
}

@Composable
private fun MainTabsBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
) {
    ManyakNavigationBar(
        items =
            MainTab.entries.map { tab ->
                ManyakNavigationItem(
                    selectedIconRes = tab.selectedIconRes,
                    unselectedIconRes = tab.unselectedIconRes,
                    nameRes = tab.nameRes,
                    selected = tab == selectedTab,
                    onSelect = { onSelectTab(tab) },
                )
            },
    )
}

/**
 * 홈이 아닌 탭에서는 홈의 시작 목적지를 밑에 깔아 둔다. 뒤로가기가 방문 순서를 거슬러 오르지 않고
 * 홈으로 한 번에 돌아오는 것이 여기서 나오고, 홈 탭에서는 밑에 아무것도 없어 앱을 벗어난다.
 */
@Composable
private fun MainTabsContent(
    selectedTab: MainTab,
    backStacks: Map<MainTab, NavBackStack<NavKey>>,
    contentPadding: PaddingValues,
    onLeaveTab: () -> Unit,
) {
    // 목적지는 백스택이 바뀔 때만 다시 만들어지므로, 그 사이에 바뀌는 여백을 값으로 붙잡으면 오래된 값이
    // 화면에 남는다. 상태로 넘겨 화면이 그릴 때마다 현재 값을 읽게 한다.
    val padding = rememberUpdatedState(contentPadding)
    val screenTransition = rememberScreenTransition()

    val homeEntries =
        rememberDecoratedNavEntries(
            backStack = backStacks.getValue(MainTab.HOME),
            entryDecorators = rememberManyakEntryDecorators(),
            entryProvider =
                entryProvider<NavKey> {
                    entry<HomeRoute> { HomeScreen(contentPadding = padding.value) }
                },
        )
    val chatEntries =
        rememberDecoratedNavEntries(
            backStack = backStacks.getValue(MainTab.CHAT),
            entryDecorators = rememberManyakEntryDecorators(),
            entryProvider =
                entryProvider<NavKey> {
                    entry<ChatListRoute> { ChatListScreen(contentPadding = padding.value) }
                },
        )
    val myEntries =
        rememberDecoratedNavEntries(
            backStack = backStacks.getValue(MainTab.MY),
            entryDecorators = rememberManyakEntryDecorators(),
            entryProvider =
                entryProvider<NavKey> {
                    entry<MyRoute> { MyScreen(contentPadding = padding.value) }
                },
        )

    val entries =
        when (selectedTab) {
            MainTab.HOME -> homeEntries
            MainTab.CHAT -> homeEntries.take(1) + chatEntries
            MainTab.MY -> homeEntries.take(1) + myEntries
        }

    NavDisplay(
        entries = entries,
        transitionSpec = screenTransition,
        popTransitionSpec = screenTransition,
        onBack = {
            val backStack = backStacks.getValue(selectedTab)
            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onLeaveTab()
        },
    )
}

@Composable
private fun rememberTabBackStacks(): Map<MainTab, NavBackStack<NavKey>> {
    val home = rememberNavBackStack(HomeRoute)
    val chat = rememberNavBackStack(ChatListRoute)
    val my = rememberNavBackStack(MyRoute)
    return remember(home, chat, my) {
        mapOf(MainTab.HOME to home, MainTab.CHAT to chat, MainTab.MY to my)
    }
}

/** 탭 안에 하위 목적지가 없는 동안에는 아무 일도 일어나지 않는다. */
private fun NavBackStack<NavKey>.popToStart() {
    while (size > 1) removeAt(lastIndex)
}
