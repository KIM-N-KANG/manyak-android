package app.manyak.create.additionalinfo.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.create.presentation.state.StorylineGenerationStore
import app.manyak.create.testing.FakeAttendanceClaimer
import app.manyak.create.testing.FakeCreditPolicyRepository
import app.manyak.create.testing.FakePendingStoryCreationStore
import app.manyak.create.testing.FakeSimpleDraftStore
import app.manyak.create.testing.FakeStoryCompletionSubmitter
import app.manyak.create.testing.FakeStoryCreationRepository
import app.manyak.create.testing.FakeTrialsRepository
import app.manyak.create.testing.FakeUserProfileRepository
import app.manyak.create.testing.TEST_DRAFT_ID
import app.manyak.create.testing.sampleGenerationInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

internal fun additionalInfoViewModel(
    store: StorylineGenerationStore,
    repository: FakeStoryCreationRepository,
    credit: CreditFixture = CreditFixture(),
) = CreateAdditionalInfoViewModel(
    TEST_DRAFT_ID,
    store,
    repository,
    credit.profiles,
    credit.policy,
    credit.trials,
    credit.attendance,
    NoOpAnalytics,
)

/** 이프 잔액, 정책, 체험. 기본은 모두 몰라 선검사가 막지 않는다. */
internal class CreditFixture(
    val profiles: FakeUserProfileRepository = FakeUserProfileRepository(initial = null),
    val policy: FakeCreditPolicyRepository = FakeCreditPolicyRepository(),
    val trials: FakeTrialsRepository = FakeTrialsRepository(),
    val attendance: FakeAttendanceClaimer = FakeAttendanceClaimer(),
)

internal class LoadedFixture(
    val repository: FakeStoryCreationRepository,
    val submitter: FakeStoryCompletionSubmitter,
    val pendingStore: FakePendingStoryCreationStore,
    val store: StorylineGenerationStore,
    val viewModel: CreateAdditionalInfoViewModel,
)

/** 스토리라인 생성 성공 결과를 스냅숏한 ViewModel 을 만든다. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.loadedViewModel(
    selectedStorylineIndex: Int? = null,
    submitter: FakeStoryCompletionSubmitter = FakeStoryCompletionSubmitter(),
    credit: CreditFixture = CreditFixture(),
    pendingStore: FakePendingStoryCreationStore = FakePendingStoryCreationStore(),
): LoadedFixture {
    val repository = FakeStoryCreationRepository()
    val store =
        StorylineGenerationStore(
            repository,
            pendingStore,
            submitter,
            this,
            FakeSimpleDraftStore(),
        ).bind(TEST_DRAFT_ID)
    store.generate(sampleGenerationInput())
    advanceUntilIdle()
    // "선택하기"로 추가 정보 단계에 들어온 상태를 만든다. 재개 지점이 이 값으로 갈린다.
    selectedStorylineIndex?.let(store::markStorylineSelected)
    return LoadedFixture(
        repository = repository,
        submitter = submitter,
        pendingStore = pendingStore,
        store = store,
        viewModel = additionalInfoViewModel(store, repository, credit),
    )
}
