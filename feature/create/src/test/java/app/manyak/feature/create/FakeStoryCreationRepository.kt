package app.manyak.feature.create

import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.story.CompletedStory
import app.manyak.core.domain.story.CreationRequestSnapshot
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.StoryCharacterInput
import app.manyak.core.domain.story.StoryCompletionCommand
import app.manyak.core.domain.story.StoryCreationRepository
import app.manyak.core.domain.story.StoryTag
import app.manyak.core.domain.story.Storyline
import app.manyak.core.domain.story.StorylineGeneration
import app.manyak.core.domain.story.StorylineGenerationCommand
import app.manyak.core.domain.story.StorylineRating
import app.manyak.core.domain.story.StorylineRecommendedInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield

internal fun sampleStorylineGeneration(simpleCreationId: Long = 10): StorylineGeneration =
    StorylineGeneration(
        simpleCreationId = simpleCreationId,
        storylines =
            listOf(
                Storyline(
                    id = 1,
                    storyline = "첫 번째 스토리라인",
                    recommendedInfos = listOf(StorylineRecommendedInfo(id = 1, text = "폐허를 자세히 그려줘")),
                ),
                Storyline(id = 2, storyline = "두 번째 스토리라인", recommendedInfos = emptyList()),
                Storyline(id = 3, storyline = "세 번째 스토리라인", recommendedInfos = emptyList()),
            ),
    )

internal fun sampleGenerationInput(): StorylineGenerationInput =
    StorylineGenerationInput(
        genreTagIds = listOf(1),
        customGenreTags = emptyList(),
        protagonist =
            StoryCharacterInput(name = null, gender = null, featureTagIds = listOf(2), customTags = emptyList()),
        supportingCharacters = emptyList(),
    )

/** 태그 결과는 고정값, 생성 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal open class FakeStoryCreationRepository(
    var tagsResult: DomainResult<List<StoryTag>> = DomainResult.Success(emptyList()),
) : StoryCreationRepository {
    val generationCommands = mutableListOf<StorylineGenerationCommand>()
    val queuedGenerationResults = ArrayDeque<DomainResult<StorylineGeneration>>()

    /** 평가 요청 기록. rating 이 null 이면 취소(DELETE) 호출이다. */
    val ratingCalls = mutableListOf<Pair<Long, StorylineRating?>>()
    val queuedRatingResults = ArrayDeque<DomainResult<Unit>>()

    override suspend fun tags(): DomainResult<List<StoryTag>> = tagsResult

    override suspend fun generateStorylines(command: StorylineGenerationCommand): DomainResult<StorylineGeneration> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다 — 관찰자가 Generating 전이를 볼 수 있어야 한다.
        yield()
        generationCommands += command
        return queuedGenerationResults.removeFirstOrNull() ?: DomainResult.Success(sampleStorylineGeneration())
    }

    val completionCommands = mutableListOf<StoryCompletionCommand>()
    val queuedCompletionResults = ArrayDeque<DomainResult<CompletedStory>>()

    override suspend fun completeStory(command: StoryCompletionCommand): DomainResult<CompletedStory> {
        yield()
        completionCommands += command
        return queuedCompletionResults.removeFirstOrNull()
            ?: DomainResult.Success(CompletedStory(id = "story-1", title = "완성 스토리"))
    }

    override suspend fun rateStoryline(
        storylineId: Long,
        rating: StorylineRating,
    ): DomainResult<Unit> {
        yield()
        ratingCalls += storylineId to rating
        return queuedRatingResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }

    override suspend fun clearStorylineRating(storylineId: Long): DomainResult<Unit> {
        yield()
        ratingCalls += storylineId to null
        return queuedRatingResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }

    val creationRequestIds = mutableListOf<String>()
    val queuedCreationRequestResults = ArrayDeque<DomainResult<CreationRequestSnapshot>>()

    override suspend fun creationRequest(requestId: String): DomainResult<CreationRequestSnapshot> {
        yield()
        creationRequestIds += requestId
        return queuedCreationRequestResults.removeFirstOrNull()
            ?: DomainResult.Success(CreationRequestSnapshot.Pending)
    }
}

/** 진행 레코드 단일 슬롯의 인메모리 구현. 기록 이력으로 영속 시점을 검증한다. */
internal class FakePendingStoryCreationStore(
    initial: PendingStoryCreation? = null,
) : PendingStoryCreationStore {
    private val state = MutableStateFlow(initial)

    override val record: Flow<PendingStoryCreation?> = state

    val current: PendingStoryCreation? get() = state.value
    val writes = mutableListOf<PendingStoryCreation>()

    override suspend fun read(): PendingStoryCreation? = state.value

    override suspend fun write(record: PendingStoryCreation) {
        writes += record
        state.value = record
    }

    override suspend fun clear() {
        state.value = null
    }
}
