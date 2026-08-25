package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class CreateKeywordViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(repository: StoryCreationRepository): CreateKeywordViewModel =
        CreateKeywordViewModel(
            storyCreationRepository = repository,
            storylineGenerationStore =
                StorylineGenerationStore(repository, FakePendingStoryCreationStore(), this),
        )

    @Test
    fun `태그 조회 실패 후 다시 불러오면 태그를 재조회한다`() =
        runTest(dispatcher) {
            val loadedTag = StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE)
            val repository = SequencedStoryCreationRepository(loadedTag)
            val viewModel = viewModel(repository)

            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.providedTags is ProvidedTags.Failed)

            viewModel.onIntent(CreateKeywordIntent.RetryTags)
            advanceUntilIdle()

            assertEquals(2, repository.requestCount)
            assertEquals(
                listOf(loadedTag),
                (viewModel.uiState.value.providedTags as ProvidedTags.Loaded)
                    .byCategory
                    .getValue(StoryTagCategory.GENRE),
            )
        }

    @Test
    fun `검증을 통과한 스토리라인 만들기는 단계 전환 효과를 낸다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(fixedTagsRepository())

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Protagonist, tagId = 2))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            val effect = withTimeoutOrNull(1_000) { viewModel.uiEffect.first() }
            assertEquals(CreateKeywordEffect.NavigateToStoryline, effect)
        }

    @Test
    fun `검증에 실패한 스토리라인 만들기는 단계 전환 효과를 내지 않는다`() =
        runTest(dispatcher) {
            val viewModel = viewModel(fixedTagsRepository())

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            assertNull(withTimeoutOrNull(1_000) { viewModel.uiEffect.first() })
            assertTrue(viewModel.uiState.value.hasAttemptedGenerate)
        }

    @Test
    fun `스토리라인 만들기는 키워드 입력을 생성 명령으로 조립해 요청한다`() =
        runTest(dispatcher) {
            val repository = fixedTagsRepository()
            val viewModel = viewModel(repository)

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1))
            viewModel.onIntent(CreateKeywordIntent.AddCustomTag(KeywordTarget.Genre, name = "타임루프"))
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Protagonist, tagId = 2))
            viewModel.onIntent(
                CreateKeywordIntent.ChangeCharacterName(KeywordTarget.Protagonist, name = " 지우 "),
            )
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            val command = repository.generationCommands.single()
            assertEquals(listOf(1L), command.genreTagIds)
            assertEquals(listOf("타임루프"), command.customGenreTags)
            assertEquals("지우", command.protagonist.name)
            assertEquals(listOf(2L), command.protagonist.featureTagIds)
            // 퍼널 진입 시 놓인 빈 주변 인물 섹션은 요청에서 빠진다.
            assertTrue(command.supportingCharacters.isEmpty())
            assertNull(command.parentCreationId)
            assertFalse(command.isRegenerated)
            // 이 화면은 스토리라인 목적지로 대체되어 사라지므로 진행 플래그는 해제되지 않는다.
            assertTrue(viewModel.uiState.value.isGeneratingStorylines)
        }

    @Test
    fun `생성 요청은 한 번만 시작되고 실패 처리는 스토리라인 화면이 담당한다`() =
        runTest(dispatcher) {
            val repository = fixedTagsRepository()
            repository.queuedGenerationResults += DomainResult.Failure(DomainError.Network)
            val viewModel = viewModel(repository)

            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Genre, tagId = 1))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.ToggleProvidedTag(KeywordTarget.Protagonist, tagId = 2))
            advanceUntilIdle()
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            viewModel.onIntent(CreateKeywordIntent.GenerateStorylines)
            advanceUntilIdle()

            // 진행 플래그가 중복 시작을 막고, 실패 상태 표시는 스토리라인 화면 몫이다.
            assertTrue(viewModel.uiState.value.isGeneratingStorylines)
            assertEquals(1, repository.generationCommands.size)
        }

    @Test
    fun `퍼널 이탈은 남은 생성 결과를 임시 저장한 뒤 이탈 효과를 낸다`() =
        runTest(dispatcher) {
            val repository = fixedTagsRepository()
            val pendingStore = FakePendingStoryCreationStore()
            val store = StorylineGenerationStore(repository, pendingStore, this)
            store.generate(sampleGenerationInput())
            advanceUntilIdle()
            val viewModel =
                CreateKeywordViewModel(
                    storyCreationRepository = repository,
                    storylineGenerationStore = store,
                )

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            assertTrue(pendingStore.current is PendingStoryCreation.Draft)
            assertEquals(
                CreateKeywordEffect.ExitFunnel(contentPreserved = true),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `남은 내용이 없는 퍼널 이탈은 저장 없이 나간다`() =
        runTest(dispatcher) {
            val pendingStore = FakePendingStoryCreationStore()
            val viewModel =
                CreateKeywordViewModel(
                    storyCreationRepository = fixedTagsRepository(),
                    storylineGenerationStore =
                        StorylineGenerationStore(fixedTagsRepository(), pendingStore, this),
                )

            viewModel.onIntent(CreateKeywordIntent.LeaveFunnel)
            advanceUntilIdle()

            assertNull(pendingStore.current)
            assertEquals(
                CreateKeywordEffect.ExitFunnel(contentPreserved = false),
                withTimeoutOrNull(1_000) { viewModel.uiEffect.first() },
            )
        }

    private fun fixedTagsRepository(): FakeStoryCreationRepository =
        FakeStoryCreationRepository(
            tagsResult =
                DomainResult.Success(
                    listOf(
                        StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE),
                        StoryTag(id = 2, name = "다정한", category = StoryTagCategory.PROTAGONIST),
                    ),
                ),
        )

    private class SequencedStoryCreationRepository(
        private val loadedTag: StoryTag,
    ) : FakeStoryCreationRepository() {
        var requestCount: Int = 0
            private set

        override suspend fun tags(): DomainResult<List<StoryTag>> {
            requestCount += 1
            return if (requestCount == 1) {
                DomainResult.Failure(DomainError.Network)
            } else {
                DomainResult.Success(listOf(loadedTag))
            }
        }
    }
}
