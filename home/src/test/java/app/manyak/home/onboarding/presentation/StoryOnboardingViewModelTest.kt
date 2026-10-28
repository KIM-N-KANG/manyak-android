package app.manyak.home.onboarding.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.StoryOnboardingDetailSource
import app.manyak.common.domain.chat.ChatStarter
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.CreatedChat
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import app.manyak.home.testing.FakeStoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryOnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val analytics = RecordingAnalytics()
    private val repository = FakeStoryRepository()
    private val chatStarter = FakeChatStarter()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = StoryOnboardingViewModel(repository, chatStarter, analytics)

    @Test
    fun `진입하면 오리지널 채팅순 첫 페이지를 읽고 진입을 한 번 센다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                listOf(StoryListQuery(StoryListFilter.ORIGINAL, StoryListSort.CHATS) to null),
                repository.requests,
            )
            assertEquals(2, viewModel.uiState.value.stories.size)
            assertEquals(listOf(AnalyticsEvent.StoryOnboardingViewed), analytics.events)
        }

    @Test
    fun `가운데에 1초 머문 카드만 스토리별로 한 번 노출로 센다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onIntent(StoryOnboardingIntent.SelectStory(0))
            runCurrent()
            advanceTimeBy(500)
            // 1초가 되기 전에 넘겼다 — 스쳐 간 카드다.
            viewModel.onIntent(StoryOnboardingIntent.SelectStory(1))
            advanceUntilIdle()
            viewModel.onIntent(StoryOnboardingIntent.SelectStory(0))
            advanceUntilIdle()
            viewModel.onIntent(StoryOnboardingIntent.SelectStory(1))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    AnalyticsEvent.StoryOnboardingCardImpressed("story-2", 1),
                    AnalyticsEvent.StoryOnboardingCardImpressed("story-1", 0),
                ),
                analytics.events.filterIsInstance<AnalyticsEvent.StoryOnboardingCardImpressed>(),
            )
            assertEquals(1, viewModel.uiState.value.selectedIndex)
        }

    @Test
    fun `채팅 시작은 가운데 스토리로 한 번만 만들고 성공하면 잠근 채 완료를 알린다`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            chatStarter.gate = gate
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onIntent(StoryOnboardingIntent.SelectStory(1))
            advanceUntilIdle()

            viewModel.onIntent(StoryOnboardingIntent.StartChat)
            viewModel.onIntent(StoryOnboardingIntent.StartChat)
            runCurrent()
            assertTrue(viewModel.uiState.value.isStartingChat)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("story-2"), chatStarter.requests)
            assertEquals(
                StoryOnboardingEffect.ChatStarted("story-2", "chat-story-2"),
                viewModel.uiEffect.first(),
            )
            assertTrue(viewModel.uiState.value.isStartingChat)
            assertEquals(
                listOf(AnalyticsEvent.StoryOnboardingChatStartClicked("story-2", 1, browsedCount = 1)),
                analytics.events.filterIsInstance<AnalyticsEvent.StoryOnboardingChatStartClicked>(),
            )
        }

    @Test
    fun `채팅 생성이 실패하면 잠금을 풀고 실패를 알린다`() =
        runTest(dispatcher) {
            chatStarter.result = DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onIntent(StoryOnboardingIntent.StartChat)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isStartingChat)
            assertEquals(StoryOnboardingEffect.ShowChatStartFailed, viewModel.uiEffect.first())
            assertTrue(analytics.events.contains(AnalyticsEvent.StoryOnboardingChatStartErrorShown("story-1")))
        }

    @Test
    fun `상세 보기와 직접 만들기는 누른 자리와 함께 기록하고 이동을 알린다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onIntent(StoryOnboardingIntent.OpenStory(1, StoryOnboardingDetailSource.BUTTON))
            advanceUntilIdle()
            assertEquals(StoryOnboardingEffect.OpenStory("story-2"), viewModel.uiEffect.first())

            viewModel.onIntent(StoryOnboardingIntent.CreateStory)
            advanceUntilIdle()
            assertEquals(StoryOnboardingEffect.OpenCreate, viewModel.uiEffect.first())

            assertEquals(
                listOf(
                    AnalyticsEvent.StoryOnboardingDetailClicked("story-2", 1, StoryOnboardingDetailSource.BUTTON),
                    AnalyticsEvent.StoryOnboardingCreateClicked(browsedCount = 0),
                ),
                analytics.events.drop(1),
            )
        }

    @Test
    fun `조회가 실패하면 실패 화면을 기록하고 다시 시도로 읽는다`() =
        runTest(dispatcher) {
            repository.queuedResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.loadFailed)
            assertTrue(analytics.events.contains(AnalyticsEvent.StoryOnboardingLoadErrorShown))

            viewModel.onIntent(StoryOnboardingIntent.Retry)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals(CarouselPhase.READY, viewModel.uiState.value.phase)
        }

    @Test
    fun `순환 페이지는 첫 스토리에서 시작하고 순번으로 돌아온다`() {
        assertEquals(0, storyIndex(loopStartPage(14), 14))
        assertEquals(13, storyIndex(loopStartPage(14) - 1, 14))
        assertEquals(0, storyIndex(loopStartPage(14) + 14, 14))
        // 한 편이면 넘길 것이 없어 순환하지 않는다.
        assertEquals(1, loopPageCount(1))
        assertEquals(0, loopStartPage(1))
    }
}

private class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }
}

private class FakeChatStarter : ChatStarter {
    val requests = mutableListOf<String>()
    var result: DomainResult<CreatedChat>? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun createChat(
        storyId: String,
        startSettingId: String?,
        personaId: String?,
    ): DomainResult<CreatedChat> {
        requests += storyId
        gate?.await()
        return result ?: DomainResult.Success(CreatedChat("chat-$storyId"))
    }
}
