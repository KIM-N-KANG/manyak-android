package app.manyak.story.detail.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.PersonaType
import app.manyak.common.domain.story.StoryLikeUpdates
import app.manyak.common.entity.persona.CreatedPersona
import app.manyak.common.entity.persona.Persona
import app.manyak.story.detail.presentation.component.truncateSummaryPart
import app.manyak.story.testing.FakeChatRepository
import app.manyak.story.testing.FakePersonaAccess
import app.manyak.story.testing.FakeStoryRepository
import app.manyak.story.testing.STORY_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailPersonaTest {
    private val dispatcher = StandardTestDispatcher()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics =
        object : Analytics {
            override fun track(event: AnalyticsEvent) {
                events += event
            }
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        personaAccess: FakePersonaAccess,
        chatRepository: FakeChatRepository = FakeChatRepository(),
    ): StoryDetailViewModel {
        val storyRepository = FakeStoryRepository()
        return StoryDetailViewModel(
            STORY_ID,
            storyRepository,
            chatRepository,
            analytics,
            storyRepository,
            storyRepository,
            StoryLikeUpdates { _, _ -> },
            personaAccess,
        )
    }

    @Test
    fun `바꾸지 않으면 기본 주인공으로 시작하고 personaId 를 싣지 않는다`() =
        runTest(dispatcher) {
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(FakePersonaAccess(listOf(SOL)), chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), chatRepository.createChatPersonaIds)
            assertEquals(
                AnalyticsEvent.ChatStartButtonClicked(STORY_ID, PersonaType.DEFAULT),
                events.filterIsInstance<AnalyticsEvent.ChatStartButtonClicked>().single(),
            )
        }

    @Test
    fun `고른 페르소나로 채팅을 만든다`() =
        runTest(dispatcher) {
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(FakePersonaAccess(listOf(SOL, DOYUN)), chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.ChoosePersona(PersonaAction.Select(DOYUN.id)))
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            assertEquals(listOf<String?>(DOYUN.id), chatRepository.createChatPersonaIds)
            assertEquals(
                AnalyticsEvent.PersonaSelected(STORY_ID, PersonaType.PERSONA),
                events.filterIsInstance<AnalyticsEvent.PersonaSelected>().single(),
            )
            assertEquals(
                PersonaType.PERSONA,
                events.filterIsInstance<AnalyticsEvent.ChatStartButtonClicked>().single().personaType,
            )
        }

    @Test
    fun `이 상세에서 만든 페르소나를 미리 고르고 채팅을 시작하면 미리 선택을 지운다`() =
        runTest(dispatcher) {
            val personaAccess = FakePersonaAccess(listOf(SOL, DOYUN))
            personaAccess.createdPersona.value = CreatedPersona(STORY_ID, DOYUN.id)
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(personaAccess, chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(DOYUN, viewModel.uiState.value.persona.selected)

            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            assertEquals(listOf<String?>(DOYUN.id), chatRepository.createChatPersonaIds)
            assertNull(personaAccess.createdPersona.value)
            // 돌아왔을 때도 시작한 주인공이 그대로 보인다.
            assertEquals(DOYUN, viewModel.uiState.value.persona.selected)
        }

    @Test
    fun `다른 스토리에서 만든 페르소나는 미리 고르지 않는다`() =
        runTest(dispatcher) {
            val personaAccess = FakePersonaAccess(listOf(SOL))
            personaAccess.createdPersona.value = CreatedPersona("other-story", SOL.id)
            val viewModel = viewModel(personaAccess)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.persona.selected)
        }

    @Test
    fun `고른 페르소나가 목록에서 사라지면 기본 주인공으로 돌아간다`() =
        runTest(dispatcher) {
            val personaAccess = FakePersonaAccess(listOf(SOL, DOYUN))
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(personaAccess, chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.ChoosePersona(PersonaAction.Select(DOYUN.id)))
            advanceUntilIdle()
            personaAccess.personas.value = listOf(SOL)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.persona.selected)
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()
            assertEquals(listOf<String?>(null), chatRepository.createChatPersonaIds)
        }

    @Test
    fun `페르소나가 10개면 생성 화면으로 가지 않고 상한을 알린다`() =
        runTest(dispatcher) {
            val full = List(10) { index -> Persona("p$index", "이름$index", "") }
            val viewModel = viewModel(FakePersonaAccess(full))

            viewModel.onIntent(StoryDetailIntent.ChoosePersona(PersonaAction.Create))
            advanceUntilIdle()

            assertEquals(
                StoryDetailEffect.ShowPersonaLimitReached,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
            assertEquals(
                AnalyticsEvent.PersonaCreateButtonClicked(STORY_ID),
                events.filterIsInstance<AnalyticsEvent.PersonaCreateButtonClicked>().single(),
            )
        }

    @Test
    fun `페르소나가 10개보다 적으면 생성 화면으로 간다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakePersonaAccess(listOf(SOL)))

            viewModel.onIntent(StoryDetailIntent.ChoosePersona(PersonaAction.Create))
            advanceUntilIdle()

            assertEquals(
                StoryDetailEffect.NavigateToPersonaCreate,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `요약은 페르소나 5자와 시작 상황 10자까지 남기고 말줄임표를 붙인다`() {
        assertEquals("윤해솔", truncateSummaryPart(" 윤해솔 ", 5))
        assertEquals("다섯글자다", truncateSummaryPart("다섯글자다", 5))
        assertEquals("여섯글자이…", truncateSummaryPart("여섯글자이름", 5))
        assertEquals("😀😀😀😀😀…", truncateSummaryPart("😀😀😀😀😀😀", 5))
    }

    private companion object {
        val SOL = Persona("persona-sol", "윤해솔", "# 주인공\n## 성별\n여성\n특징")
        val DOYUN = Persona("persona-doyun", "강도윤", "# 주인공\n## 성별\n남성\n특징")
        const val TIMEOUT_MILLIS = 1_000L
    }
}
