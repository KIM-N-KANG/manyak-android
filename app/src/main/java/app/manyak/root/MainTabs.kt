package app.manyak.root

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.navigation.ChatListRoute
import app.manyak.core.navigation.HomeRoute
import app.manyak.core.navigation.MyRoute
import app.manyak.core.navigation.StudioRoute
import app.manyak.core.ui.component.ManyakBrandHeader
import app.manyak.core.ui.component.ManyakNavigationBar
import app.manyak.core.ui.component.ManyakNavigationItem
import app.manyak.core.ui.component.ManyakSectionHeader
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.chat.ChatListScreen
import app.manyak.feature.home.HomeScreen
import app.manyak.feature.my.MyScreen
import app.manyak.feature.studio.StudioScreen

/**
 * 하단 탭 넷을 두르는 셸. 헤더와 하단 바를 여기서만 그리고, 탭 화면에는 chrome 이 차지한 여백만 넘긴다.
 *
 * **탭마다 백스택을 따로 소유한다.** 탭을 옮겨도 떠난 탭의 백스택과 그에 묶인 상태가 남아 있어,
 * 돌아왔을 때 스크롤 위치와 목록 상태가 유지된다. 탭 전환 자체는 어느 백스택을 그릴지 고르는 일이라
 * 이력에 쌓이지 않는다.
 */
@Composable
@Suppress("LongParameterList")
internal fun MainTabsScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onCreateStory: () -> Unit,
    onResumeCreation: (CreationResumePoint) -> Unit,
    onOpenInvite: () -> Unit,
    onOpenServiceInfo: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenWithdrawal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStacks = rememberTabBackStacks()

    Scaffold(
        modifier = modifier,
        containerColor = ManyakTheme.colors.surface,
        // 홈은 로고가 그 자리의 이름을 대신하고, 나머지 탭은 이름만 둔다.
        topBar = {
            if (selectedTab == MainTab.HOME) {
                ManyakBrandHeader()
            } else {
                ManyakSectionHeader(titleRes = selectedTab.nameRes)
            }
        },
        bottomBar = {
            MainTabsBar(
                selectedTab = selectedTab,
                // 이미 선택된 탭을 다시 누르면 그 탭의 시작 목적지로 되돌린다.
                onSelectTab = { tab ->
                    if (tab == selectedTab) backStacks.getValue(tab).popToStart() else onSelectTab(tab)
                },
            )
        },
    ) { innerPadding ->
        MainTabsContent(
            selectedTab = selectedTab,
            backStacks = backStacks,
            contentPadding = innerPadding,
            onLeaveTab = { onSelectTab(MainTab.HOME) },
            onOpenStory = onOpenStory,
            onOpenChat = onOpenChat,
            // 빈 채팅 목록의 안내가 제작으로 보내는 자리. 목적지를 쌓지 않고 탭을 바꾸는 것이 핵심이다 —
            // push 하면 뒤로가기가 채팅 탭으로 되돌아와, 탭 전환이 이력에 쌓이지 않는다는 규칙이 깨진다.
            onGoToStudio = { onSelectTab(MainTab.STUDIO) },
            onCreateStory = onCreateStory,
            onResumeCreation = onResumeCreation,
            onOpenInvite = onOpenInvite,
            onOpenServiceInfo = onOpenServiceInfo,
            onOpenFeedback = onOpenFeedback,
            onOpenWithdrawal = onOpenWithdrawal,
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
@Suppress("LongParameterList")
private fun MainTabsContent(
    selectedTab: MainTab,
    backStacks: Map<MainTab, NavBackStack<NavKey>>,
    contentPadding: PaddingValues,
    onLeaveTab: () -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onGoToStudio: () -> Unit,
    onCreateStory: () -> Unit,
    onResumeCreation: (CreationResumePoint) -> Unit,
    onOpenInvite: () -> Unit,
    onOpenServiceInfo: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenWithdrawal: () -> Unit,
) {
    // 목적지는 백스택이 바뀔 때만 다시 만들어지므로, 그 사이에 바뀌는 여백을 값으로 붙잡으면 오래된 값이
    // 화면에 남는다. 상태로 넘겨 화면이 그릴 때마다 현재 값을 읽게 한다.
    val padding = rememberUpdatedState(contentPadding)
    val screenTransition = rememberScreenTransition()

    val homeEntries =
        rememberTabEntries(backStacks.getValue(MainTab.HOME)) {
            entry<HomeRoute> { HomeScreen(contentPadding = padding.value, onOpenStory = onOpenStory) }
        }
    val chatEntries =
        rememberTabEntries(backStacks.getValue(MainTab.CHAT)) {
            entry<ChatListRoute> {
                ChatListScreen(
                    contentPadding = padding.value,
                    onOpenChat = onOpenChat,
                    onGoToStudio = onGoToStudio,
                )
            }
        }
    val studioEntries =
        rememberTabEntries(backStacks.getValue(MainTab.STUDIO)) {
            entry<StudioRoute> {
                StudioScreen(
                    contentPadding = padding.value,
                    onOpenStory = onOpenStory,
                    onCreateStory = onCreateStory,
                    onResumeCreation = onResumeCreation,
                )
            }
        }
    val myEntries =
        rememberTabEntries(backStacks.getValue(MainTab.MY)) {
            entry<MyRoute> {
                MyScreen(
                    contentPadding = padding.value,
                    onOpenInvite = onOpenInvite,
                    onOpenServiceInfo = onOpenServiceInfo,
                    onOpenFeedback = onOpenFeedback,
                    onOpenWithdrawal = onOpenWithdrawal,
                )
            }
        }

    val entries =
        when (selectedTab) {
            MainTab.HOME -> homeEntries
            MainTab.CHAT -> homeEntries.take(1) + chatEntries
            MainTab.STUDIO -> homeEntries.take(1) + studioEntries
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

/** 탭마다 백스택과 등록할 목적지만 다르고 데코레이터는 같으므로, 그 배선은 여기 한 곳에만 둔다. */
@Composable
private fun rememberTabEntries(
    backStack: NavBackStack<NavKey>,
    builder: EntryProviderScope<NavKey>.() -> Unit,
): List<NavEntry<NavKey>> =
    rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = rememberManyakEntryDecorators(),
        entryProvider = entryProvider(builder = builder),
    )

@Composable
private fun rememberTabBackStacks(): Map<MainTab, NavBackStack<NavKey>> {
    val home = rememberNavBackStack(HomeRoute)
    val chat = rememberNavBackStack(ChatListRoute)
    val studio = rememberNavBackStack(StudioRoute)
    val my = rememberNavBackStack(MyRoute)
    return remember(home, chat, studio, my) {
        mapOf(
            MainTab.HOME to home,
            MainTab.CHAT to chat,
            MainTab.STUDIO to studio,
            MainTab.MY to my,
        )
    }
}

/** 탭 안에 하위 목적지가 없는 동안에는 아무 일도 일어나지 않는다. */
private fun NavBackStack<NavKey>.popToStart() {
    while (size > 1) removeAt(lastIndex)
}
