package app.manyak.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryOnboardingAction
import app.manyak.analytics.entity.StoryOnboardingChatSource
import app.manyak.core.navigation.ChatRoomRoute
import app.manyak.core.navigation.CreateKeywordRoute
import app.manyak.core.navigation.MainTabsRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StoryOnboardingRoute
import app.manyak.home.onboarding.presentation.StoryOnboardingScreen
import java.util.UUID

/** 온보딩을 끝내는 길. 돌아갈 탭과 완료 이벤트를 받는다. 온보딩이 루트가 아니면 아무것도 하지 않는다. */
internal typealias FinishStoryOnboarding = (MainTab, AnalyticsEvent.StoryOnboardingCompleted) -> Unit

/**
 * 온보딩 루트를 셸로 바꿔 끼우는 동작을 만든다. 루트만 바꾸므로 그 위에 쌓인 상세는 그대로 남고, 그 뒤의
 * 뒤로가기가 고른 탭으로 간다.
 *
 * 가입 기록이 그래프보다 늦게 도착했으면 아직 셸에서 아무 데도 가지 않았을 때만 루트를 온보딩으로 바꾼다.
 */
@Composable
internal fun rememberOnboardingFinisher(
    backStack: MutableList<NavKey>,
    pending: Boolean,
    viewModel: RootViewModel,
    onSelectTab: (MainTab) -> Unit,
): FinishStoryOnboarding {
    LaunchedEffect(pending) {
        if (pending && backStack.singleOrNull() == MainTabsRoute) backStack[0] = StoryOnboardingRoute
    }
    val latestOnSelectTab by rememberUpdatedState(onSelectTab)
    return { tab, completion ->
        if (backStack.firstOrNull() == StoryOnboardingRoute) {
            backStack[0] = MainTabsRoute
            latestOnSelectTab(tab)
            viewModel.onStoryOnboardingFinished(completion)
        }
    }
}

/** 신규 가입 회원의 첫 화면. 상세는 그 위에 쌓아 뒤로가기가 온보딩으로 돌아온다. */
internal fun EntryProviderScope<NavKey>.storyOnboardingEntry(
    backStack: MutableList<NavKey>,
    finishOnboarding: FinishStoryOnboarding,
) {
    entry<StoryOnboardingRoute> {
        StoryOnboardingScreen(
            onOpenStory = { storyId -> backStack.push(StoryDetailRoute(storyId)) },
            // 채팅방 뒤로가기는 홈 탭이다 — 온보딩은 이미 끝났다.
            onChatStarted = { storyId, chatId ->
                finishOnboarding(MainTab.HOME, chatCompleted(StoryOnboardingChatSource.CAROUSEL, storyId, chatId))
                backStack.push(ChatRoomRoute(chatId))
            },
            // 퍼널 이탈과 완성 제출이 모두 제작 탭으로 돌아오도록 그 탭을 골라 둔다.
            onCreateStory = {
                finishOnboarding(
                    MainTab.STUDIO,
                    AnalyticsEvent.StoryOnboardingCompleted(action = StoryOnboardingAction.CREATE),
                )
                backStack.push(CreateKeywordRoute(UUID.randomUUID().toString()))
            },
        )
    }
}

/** 상세에서 채팅방을 연다. 온보딩 위에서 연 상세였다면 먼저 온보딩을 끝내 뒤로가기가 상세, 그다음 홈 탭이 된다. */
internal fun MutableList<NavKey>.enterChat(finishOnboarding: FinishStoryOnboarding): (String, String) -> Unit =
    { storyId, chatId ->
        finishOnboarding(MainTab.HOME, chatCompleted(StoryOnboardingChatSource.STORY_DETAIL, storyId, chatId))
        push(ChatRoomRoute(chatId))
    }

private fun chatCompleted(
    source: StoryOnboardingChatSource,
    storyId: String,
    chatId: String,
) = AnalyticsEvent.StoryOnboardingCompleted(StoryOnboardingAction.CHAT, source, storyId, chatId)
