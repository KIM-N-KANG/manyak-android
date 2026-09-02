package app.manyak.feature.story

import app.manyak.core.domain.chat.CreatedChat
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryReportReason
import app.manyak.core.ui.report.StoryReportAction
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        storyRepository: FakeStoryRepository = FakeStoryRepository(),
        chatRepository: FakeChatRepository = FakeChatRepository(),
    ) = StoryDetailViewModel(STORY_ID, storyRepository, chatRepository)

    @Test
    fun `화면이 보이면 상세를 조회하고 첫 시작 설정을 고른다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(STORY_ID, state.story?.id)
            assertEquals("setting-a", state.selectedStartSettingId)
        }

    @Test
    fun `이미 본문이 있으면 복귀 갱신이 골격도 실패 화면도 띄우지 않는다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            // 채팅방에서 돌아온 자리 — 갱신이 실패해도 보고 있던 본문이 남아야 한다.
            storyRepository.queuedDetailResults += DomainResult.Failure(DomainError.Network)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, storyRepository.storyDetailCallCount)
            assertFalse(state.isLoading)
            assertNull(state.loadError)
            assertEquals(STORY_ID, state.story?.id)
        }

    @Test
    fun `복귀 갱신은 플레이한 만큼 늘어난 턴 수와 본 엔딩을 반영한다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(
                128L,
                viewModel.uiState.value.story
                    ?.turnCount,
            )

            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(turnCount = 131, reachedEndings = listOf("시계탑의 아침")))
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            val story = viewModel.uiState.value.story
            assertEquals(131L, story?.turnCount)
            assertEquals(listOf("시계탑의 아침"), story?.reachedEndings)
        }

    @Test
    fun `갱신은 고른 시작 설정을 유지하고 사라졌으면 첫 번째로 되돌린다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.SelectStartSetting("setting-b"))
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals("setting-b", viewModel.uiState.value.selectedStartSettingId)

            // 서버에서 그 설정이 사라지면 첫 번째로 되돌아간다.
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(startSettings = sampleStartSettings().take(1)))
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals("setting-a", viewModel.uiState.value.selectedStartSettingId)
        }

    @Test
    fun `채팅 시작은 고른 시작 설정으로 채팅을 만들고 채팅방으로 넘긴다`() =
        runTest(dispatcher) {
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(chatRepository = chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.SelectStartSetting("setting-b"))
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            assertEquals(listOf("setting-b"), chatRepository.createChatStartSettingIds)
            assertEquals(
                StoryDetailEffect.NavigateToChat("chat-1"),
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `시작 설정이 없으면 서버 폴백으로 채팅을 만든다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail(startSettings = emptyList()))
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(storyRepository = storyRepository, chatRepository = chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), chatRepository.createChatStartSettingIds)
        }

    @Test
    fun `연타해도 채팅은 한 번만 만들어진다`() =
        runTest(dispatcher) {
            val chatRepository = FakeChatRepository()
            val viewModel = viewModel(chatRepository = chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.StartChat)
            viewModel.onIntent(StoryDetailIntent.StartChat)
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            assertEquals(1, chatRepository.createChatStartSettingIds.size)
        }

    @Test
    fun `채팅 시작이 실패하면 잠금을 풀고 문구를 남긴다`() =
        runTest(dispatcher) {
            val chatRepository = FakeChatRepository()
            chatRepository.queuedCreateChatResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(chatRepository = chatRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isStartingChat)
            assertTrue(state.startChatFailed)

            // 다시 누를 수 있어야 한다.
            chatRepository.queuedCreateChatResults += DomainResult.Success(CreatedChat(id = "chat-2"))
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()
            assertEquals(2, chatRepository.createChatStartSettingIds.size)
        }

    @Test
    fun `채팅방에서 돌아오면 시작 버튼 잠금이 풀린다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.StartChat)
            advanceUntilIdle()
            // 성공 직후에 풀면 화면이 사라지는 중에 버튼이 되살아나 깜빡인다.
            assertTrue(viewModel.uiState.value.isStartingChat)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isStartingChat)
        }

    @Test
    fun `없는 스토리는 재시도 없는 오류로 구분한다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Failure(DomainError.Server(status = 404, code = null, requestId = null))
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            assertEquals(StoryDetailLoadError.NOT_FOUND, viewModel.uiState.value.loadError)
        }

    @Test
    fun `네트워크 실패는 재시도로 복구된다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(StoryDetailLoadError.GENERAL, viewModel.uiState.value.loadError)

            viewModel.onIntent(StoryDetailIntent.Retry)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.loadError)
            assertEquals(STORY_ID, state.story?.id)
        }

    @Test
    fun `조회가 도는 동안 들어온 조회는 겹쳐 나가지 않는다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.inFlightGate = CompletableDeferred()
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            assertEquals(1, storyRepository.storyDetailCallCount)

            storyRepository.inFlightGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                STORY_ID,
                viewModel.uiState.value.story
                    ?.id,
            )
        }

    @Test
    fun `썸네일이 없으면 뷰어가 열리지 않는다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDetailResults +=
                DomainResult.Success(sampleStoryDetail().copy(thumbnailUrl = null))
            val viewModel = viewModel(storyRepository = storyRepository)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.OpenImageViewer)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isImageViewerOpen)
        }

    @Test
    fun `뷰어는 열고 닫을 수 있다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.OpenImageViewer)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isImageViewerOpen)

            viewModel.onIntent(StoryDetailIntent.CloseImageViewer)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isImageViewerOpen)
        }

    @Test
    fun `신고를 접수하면 시트를 닫고 입력을 비운다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.Open))
            viewModel.onIntent(
                StoryDetailIntent.Report(StoryReportAction.SelectReason(StoryReportReason.INAPPROPRIATE)),
            )
            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.ChangeDetail("불쾌한 묘사가 있어요")))
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.Submit))
            advanceUntilIdle()

            assertEquals(
                listOf(Triple(STORY_ID, StoryReportReason.INAPPROPRIATE, "불쾌한 묘사가 있어요")),
                storyRepository.reportedStories,
            )
            val state = viewModel.uiState.value
            assertFalse(state.report.isSheetOpen)
            assertNull(state.report.reason)
            assertEquals("", state.report.detail)
        }

    @Test
    fun `신고가 실패하면 시트와 입력을 그대로 둔다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedReportResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.Open))
            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.SelectReason(StoryReportReason.SPAM)))
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.Submit))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.report.isSheetOpen)
            assertEquals(StoryReportReason.SPAM, state.report.reason)
            assertFalse(state.report.isSubmitting)
        }

    @Test
    fun `조회 전에는 신고 시트를 열지 않는다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(StoryDetailIntent.Report(StoryReportAction.Open))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.report.isSheetOpen)
        }
}

private const val TIMEOUT_MILLIS = 1_000L
