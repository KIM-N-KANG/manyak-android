package app.manyak.create.additionalinfo.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.AttendanceResult
import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.entity.credit.Trials
import app.manyak.common.presentation.credit.AttendanceOutcome
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.presentation.state.StorylineGenerationState
import app.manyak.create.testing.FakeAttendanceClaimer
import app.manyak.create.testing.FakeCreditPolicyRepository
import app.manyak.create.testing.FakePendingStoryCreationStore
import app.manyak.create.testing.FakeTrialsRepository
import app.manyak.create.testing.FakeUserProfileRepository
import app.manyak.create.testing.TEST_DRAFT_ID
import app.manyak.create.testing.sampleProfile
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

/** 완성 전 잔액 선검사와 이프 부족 시트. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateAdditionalInfoCreditShortageTest {
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
    fun `체험을 다 쓰고 아는 잔액이 완성 비용보다 적으면 완성 요청 없이 이프 부족 시트를 연다`() =
        runTest(dispatcher) {
            val fixture = loadedViewModel(credit = shortCredit())
            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.SelectArtStyle("ANIME"))
            advanceUntilIdle()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
            advanceUntilIdle()

            assertTrue(fixture.submitter.submitted.isEmpty())
            assertFalse(fixture.viewModel.uiState.value.isSubmitting)
            assertEquals(
                true,
                fixture.viewModel.uiState.value.creditShortage
                    ?.canClaimAttendance,
            )
            assertTrue(fixture.store.state.value is StorylineGenerationState.Generated)
        }

    @Test
    fun `시트의 충전은 입력을 초안으로 저장한 뒤 퍼널을 떠난다`() =
        runTest(dispatcher) {
            val fixture = shortCreditSheet()

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CreditShortageCharge)
            advanceUntilIdle()

            val record = fixture.pendingStore.read(TEST_DRAFT_ID) as PendingStoryCreation.Draft
            assertEquals(listOf("배경은 서울"), record.progress.additionalInfoInputs)
            assertEquals(CreateAdditionalInfoEffect.OpenCreditCharge, fixture.viewModel.uiEffect.first())
            assertEquals(StorylineGenerationState.Idle, fixture.store.state.value)
        }

    @Test
    fun `초안을 저장하지 못하면 충전으로 떠나지 않고 시트에 머문다`() =
        runTest(dispatcher) {
            val fixture = shortCreditSheet(FakePendingStoryCreationStore(writeSucceeds = false))

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CreditShortageCharge)
            advanceUntilIdle()

            assertEquals(
                false,
                fixture.viewModel.uiState.value.creditShortage
                    ?.isCharging,
            )
            assertNull(withTimeoutOrNull(1_000) { fixture.viewModel.uiEffect.first() })
            assertTrue(fixture.store.state.value is StorylineGenerationState.Generated)
        }

    @Test
    fun `시트의 출석이 이미 출석 응답이어도 알리고 시트를 닫는다`() =
        runTest(dispatcher) {
            val attendance =
                FakeAttendanceClaimer(DomainResult.Success(AttendanceResult(rewarded = false, amount = null)))
            val fixture = shortCreditSheet(credit = shortCredit(attendance))

            fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CreditShortageAttendance)
            advanceUntilIdle()

            assertEquals(
                CreateAdditionalInfoEffect.ShowAttendanceOutcome(AttendanceOutcome.AlreadyDone),
                fixture.viewModel.uiEffect.first(),
            )
            assertNull(fixture.viewModel.uiState.value.creditShortage)
        }

    @Test
    fun `완성 잔액 선검사는 잔액, 정책, 체험 중 하나라도 모르거나 체험이 남았으면 막지 않는다`() {
        val profile = sampleProfile().copy(creditBalance = 100)
        val policy = CreditPolicy(storyCreationCost = 300)
        val exhausted = Trials(storyCreation = TrialUsage(used = 1, limit = 1))

        assertTrue(isStoryCreationCreditShort(profile, policy, exhausted))
        assertFalse(isStoryCreationCreditShort(null, policy, exhausted))
        assertFalse(isStoryCreationCreditShort(profile, CreditPolicy(), exhausted))
        assertFalse(isStoryCreationCreditShort(profile, policy, null))
        assertFalse(isStoryCreationCreditShort(profile, policy, Trials()))
        assertFalse(isStoryCreationCreditShort(profile, policy, Trials(storyCreation = TrialUsage(0, 1))))
        assertFalse(isStoryCreationCreditShort(profile, policy, Trials(storyCreation = TrialUsage(5, null))))
        assertFalse(isStoryCreationCreditShort(profile.copy(creditBalance = 300), policy, exhausted))
    }

    /** 체험을 다 쓰고 잔액이 완성 비용보다 적으며 오늘 출석하지 않았다. */
    private fun shortCredit(attendance: FakeAttendanceClaimer = FakeAttendanceClaimer()) =
        CreditFixture(
            profiles = FakeUserProfileRepository(sampleProfile().copy(creditBalance = 100)),
            policy = FakeCreditPolicyRepository(CreditPolicy(storyCreationCost = 300)),
            trials = FakeTrialsRepository(Trials(storyCreation = TrialUsage(used = 1, limit = 1))),
            attendance = attendance,
        )

    /** 입력을 넣고 완성을 눌러 이프 부족 시트가 열린 상태. */
    private fun TestScope.shortCreditSheet(
        pendingStore: FakePendingStoryCreationStore = FakePendingStoryCreationStore(),
        credit: CreditFixture = shortCredit(),
    ): LoadedFixture {
        val fixture = loadedViewModel(selectedStorylineIndex = 0, credit = credit, pendingStore = pendingStore)
        fixture.viewModel.onIntent(CreateAdditionalInfoIntent.ChangeInput(inputId = 0, value = "배경은 서울"))
        fixture.viewModel.onIntent(CreateAdditionalInfoIntent.SelectArtStyle("ANIME"))
        advanceUntilIdle()
        fixture.viewModel.onIntent(CreateAdditionalInfoIntent.CompleteStory(storylineIndex = 0))
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.creditShortage != null)
        return fixture
    }
}
