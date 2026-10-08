package app.manyak.story.detail.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryLikeUpdates
import app.manyak.common.entity.chat.CreatedChat
import app.manyak.report.entity.StoryReportReason
import app.manyak.report.presentation.StoryReportAction
import app.manyak.story.entity.StoryCharacter
import app.manyak.story.testing.FakeChatRepository
import app.manyak.story.testing.FakePersonaAccess
import app.manyak.story.testing.FakeStoryRepository
import app.manyak.story.testing.STORY_ID
import app.manyak.story.testing.sampleStartSettings
import app.manyak.story.testing.sampleStoryDetail
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
        likeUpdates: StoryLikeUpdates = StoryLikeUpdates { _, _ -> },
    ) = StoryDetailViewModel(
        STORY_ID,
        storyRepository,
        chatRepository,
        NoOpAnalytics,
        storyRepository,
        storyRepository,
        likeUpdates,
        FakePersonaAccess(),
    )

    @Test
    fun `업로드한 주변 인물 이미지도 상세 뷰어로 연다`() =
        runTest(dispatcher) {
            val url = "https://dev-cdn.manyak.app/characters/uploaded/clockmaker.webp"
            val repository = FakeStoryRepository()
            repository.queuedDetailResults +=
                DomainResult.Success(
                    sampleStoryDetail().copy(characters = listOf(StoryCharacter("시계공", url, "시계를 고칩니다"))),
                )
            val viewModel = viewModel(repository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.OpenCharacterImage(url))
            advanceUntilIdle()
            assertEquals(url, viewModel.uiState.value.imageViewerUrl)
        }

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
    fun `채팅 시작이 실패하면 잠금을 풀고 실패 토스트를 보낸다`() =
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
            assertEquals(StoryDetailEffect.ShowChatStartFailed, viewModel.uiEffect.first())

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
            assertEquals(2, storyRepository.storyDetailCallCount)
        }

    @Test
    fun `수정 복귀가 이전 조회와 겹쳐도 다시 조회한 수정 내용이 최종 상태가 된다`() =
        runTest(dispatcher) {
            val repository = FakeStoryRepository()
            val gate = CompletableDeferred<Unit>()
            repository.inFlightGate = gate
            repository.queuedDetailResults += DomainResult.Success(sampleStoryDetail())
            repository.queuedDetailResults += DomainResult.Success(sampleStoryDetail().copy(title = "수정된 제목"))
            val model = viewModel(repository)
            model.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            model.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(1, repository.storyDetailCallCount)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(2, repository.storyDetailCallCount)
            assertEquals(
                "수정된 제목",
                model.uiState.value.story
                    ?.title,
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

            assertNull(viewModel.uiState.value.imageViewerUrl)
        }

    @Test
    fun `뷰어는 열고 닫을 수 있다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.OpenImageViewer)
            advanceUntilIdle()
            assertEquals(sampleStoryDetail().thumbnailUrl, viewModel.uiState.value.imageViewerUrl)

            viewModel.onIntent(StoryDetailIntent.CloseImageViewer)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.imageViewerUrl)
        }

    @Test
    fun `인물 뷰어는 갱신에도 같은 이미지를 유지하고 이미지가 사라지면 닫힌다`() =
        runTest(dispatcher) {
            val url = "https://cdn.manyak.app/characters/originals/clockmaker.png"
            val story = sampleStoryDetail().copy(characters = listOf(StoryCharacter(name = "시계공", imageUrl = url)))
            val repository = FakeStoryRepository()
            repository.queuedDetailResults += DomainResult.Success(story)
            repository.queuedDetailResults += DomainResult.Success(story.copy(thumbnailUrl = null))
            val events = mutableListOf<AnalyticsEvent>()
            val analytics =
                object : Analytics {
                    override fun track(event: AnalyticsEvent) {
                        events += event
                    }
                }
            val viewModel =
                StoryDetailViewModel(
                    STORY_ID,
                    repository,
                    FakeChatRepository(),
                    analytics,
                    repository,
                    repository,
                    StoryLikeUpdates {
                        _,
                        _,
                        ->
                    },
                    FakePersonaAccess(),
                )
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            viewModel.onIntent(StoryDetailIntent.OpenCharacterImage(url))
            advanceUntilIdle()
            assertEquals(url, viewModel.uiState.value.imageViewerUrl)

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertEquals(url, viewModel.uiState.value.imageViewerUrl)
            assertEquals(
                listOf(AnalyticsEvent.StoryDetailCharacterImageClicked(STORY_ID)),
                events.filterIsInstance<AnalyticsEvent.StoryDetailCharacterImageClicked>(),
            )

            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.imageViewerUrl)
        }

    @Test
    fun `현재 인물에 없는 이미지와 허용하지 않은 주소는 열지 않는다`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()
            val urls =
                listOf("https://cdn.manyak.app/characters/originals/absent.png", "https://evil.example/a.png", "")
            urls.forEach { url ->
                viewModel.onIntent(StoryDetailIntent.OpenCharacterImage(url))
                advanceUntilIdle()
                assertNull(viewModel.uiState.value.imageViewerUrl)
            }
        }

    @Test
    fun `상황 설명의 장면 이미지는 인물 이미지 클릭으로 세지 않고 연다`() =
        runTest(dispatcher) {
            val url = "https://cdn.manyak.app/scenes/originals/s1/platform_1a2b3c4d.webp"
            val story =
                sampleStoryDetail().copy(
                    startSettings = sampleStartSettings().map { it.copy(startSituation = "안개.\n\n[[$url]]") },
                )
            val repository = FakeStoryRepository()
            repository.queuedDetailResults += DomainResult.Success(story)
            val events = mutableListOf<AnalyticsEvent>()
            val viewModel =
                StoryDetailViewModel(
                    STORY_ID,
                    repository,
                    FakeChatRepository(),
                    object : Analytics {
                        override fun track(event: AnalyticsEvent) {
                            events += event
                        }
                    },
                    repository,
                    repository,
                    StoryLikeUpdates { _, _ -> },
                    FakePersonaAccess(),
                )
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.OpenCharacterImage(url))
            advanceUntilIdle()

            assertEquals(url, viewModel.uiState.value.imageViewerUrl)
            assertTrue(events.none { it is AnalyticsEvent.StoryDetailCharacterImageClicked })
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

    @Test
    fun `삭제를 확인하면 지우고 화면을 떠나라는 효과를 낸다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.RequestDelete)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isDeleteDialogOpen)

            viewModel.onIntent(StoryDetailIntent.ConfirmDelete)
            advanceUntilIdle()

            assertEquals(listOf(STORY_ID), storyRepository.deletedStoryIds)
            assertEquals(
                StoryDetailEffect.StoryDeleted,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `삭제 실패는 다이얼로그를 닫고 실패를 알리되 본문은 그대로 둔다`() =
        runTest(dispatcher) {
            val storyRepository = FakeStoryRepository()
            storyRepository.queuedDeleteResults += DomainResult.Failure(DomainError.Unknown)
            val viewModel = viewModel(storyRepository = storyRepository)
            viewModel.onIntent(StoryDetailIntent.ScreenShown)
            advanceUntilIdle()

            viewModel.onIntent(StoryDetailIntent.RequestDelete)
            viewModel.onIntent(StoryDetailIntent.ConfirmDelete)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isDeleteDialogOpen)
            assertFalse(state.isDeleting)
            assertTrue(state.story != null)
            assertEquals(
                StoryDetailEffect.ShowDeleteFailed,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
        }
}

private const val TIMEOUT_MILLIS = 1_000L
