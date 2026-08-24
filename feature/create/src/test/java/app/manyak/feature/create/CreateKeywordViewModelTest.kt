package app.manyak.feature.create

import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.StoryTagCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun `태그 조회 실패 후 다시 불러오면 태그를 재조회한다`() =
        runTest(dispatcher) {
            val loadedTag = StoryTag(id = 1, name = "로맨스", category = StoryTagCategory.GENRE)
            val repository = SequencedStoryCreationRepository(loadedTag)
            val viewModel = CreateKeywordViewModel(repository)

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

    private class SequencedStoryCreationRepository(
        private val loadedTag: StoryTag,
    ) : StoryCreationRepository {
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
