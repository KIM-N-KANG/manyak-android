package app.manyak.my.persona.presentation

import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.persona.CreatedPersona
import app.manyak.common.entity.persona.Persona
import app.manyak.my.persona.domain.PersonaRepository
import app.manyak.my.persona.entity.PersonaGender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonaFormViewModelTest {
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
    fun `빈 입력은 보내지 않고 칸을 고치면 그 칸의 오류만 지운다`() =
        runTest(dispatcher) {
            val repository = FakePersonaRepository()
            val viewModel = PersonaFormViewModel(null, null, repository, NoOpAnalytics)

            viewModel.onIntent(PersonaFormIntent.NameChanged("  "))
            viewModel.onIntent(PersonaFormIntent.Submit)
            advanceUntilIdle()
            val failed = viewModel.uiState.value
            assertTrue(failed.nameError && failed.genderError && failed.featureError)

            viewModel.onIntent(PersonaFormIntent.NameChanged("윤해솔"))
            advanceUntilIdle()
            val named = viewModel.uiState.value
            assertFalse(named.nameError)
            assertTrue(named.genderError && named.featureError)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun `생성은 앞뒤 공백을 뺀 이름과 형식에 맞춘 소개를 출발 스토리와 함께 보낸다`() =
        runTest(dispatcher) {
            val repository = FakePersonaRepository()
            val viewModel = PersonaFormViewModel(null, "story-1", repository, NoOpAnalytics)

            viewModel.onIntent(PersonaFormIntent.NameChanged(" 윤해솔 "))
            viewModel.onIntent(PersonaFormIntent.GenderChanged(PersonaGender.FEMALE))
            viewModel.onIntent(PersonaFormIntent.FeatureChanged(" 겁이 많다 "))
            advanceUntilIdle()
            viewModel.onIntent(PersonaFormIntent.Submit)
            advanceUntilIdle()

            assertEquals(
                listOf(Triple("윤해솔", "# 주인공\n## 성별\n여성\n겁이 많다", "story-1")),
                repository.created,
            )
            assertEquals(
                PersonaFormEffect.Saved(isEdit = false),
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
        }

    @Test
    fun `10개를 넘긴 생성은 상한을 알리고 입력을 남긴다`() =
        runTest(dispatcher) {
            val repository = FakePersonaRepository()
            repository.createResult = DomainResult.Failure(DomainError.Server(409, null, null))
            val viewModel = PersonaFormViewModel(null, null, repository, NoOpAnalytics)

            viewModel.onIntent(PersonaFormIntent.NameChanged("윤해솔"))
            viewModel.onIntent(PersonaFormIntent.GenderChanged(PersonaGender.FEMALE))
            viewModel.onIntent(PersonaFormIntent.FeatureChanged("특징"))
            advanceUntilIdle()
            viewModel.onIntent(PersonaFormIntent.Submit)
            advanceUntilIdle()

            assertEquals(
                PersonaFormEffect.ShowLimitReached,
                withTimeoutOrNull(TIMEOUT_MILLIS) { viewModel.uiEffect.first() },
            )
            assertEquals("윤해솔", viewModel.uiState.value.name)
            assertFalse(viewModel.uiState.value.isSubmitting)
        }

    @Test
    fun `수정은 기존 소개를 성별과 특징으로 나눠 채우고 PATCH 로 저장한다`() =
        runTest(dispatcher) {
            val repository =
                FakePersonaRepository(listOf(Persona("p1", "윤해솔", "# 주인공\n## 성별\n남성\n## 말투\n존댓말")))
            val viewModel = PersonaFormViewModel("p1", null, repository, NoOpAnalytics)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PersonaFormLoad.READY, state.load)
            assertEquals(PersonaGender.MALE, state.gender)
            assertEquals("## 말투\n존댓말", state.feature)

            viewModel.onIntent(PersonaFormIntent.NameChanged("새 이름"))
            advanceUntilIdle()
            viewModel.onIntent(PersonaFormIntent.Submit)
            advanceUntilIdle()

            assertEquals(listOf(Triple("p1", "새 이름", "# 주인공\n## 성별\n남성\n## 말투\n존댓말")), repository.updated)
        }

    @Test
    fun `목록에 없는 페르소나는 찾을 수 없다고 보인다`() =
        runTest(dispatcher) {
            val viewModel = PersonaFormViewModel("missing", null, FakePersonaRepository(emptyList()), NoOpAnalytics)
            advanceUntilIdle()

            assertEquals(PersonaFormLoad.NOT_FOUND, viewModel.uiState.value.load)
        }

    private class FakePersonaRepository(
        initial: List<Persona>? = null,
    ) : PersonaRepository {
        override val personas = MutableStateFlow(initial)
        override val createdPersona = MutableStateFlow<CreatedPersona?>(null)
        val created = mutableListOf<Triple<String, String, String?>>()
        val updated = mutableListOf<Triple<String, String, String>>()
        var createResult: DomainResult<Persona>? = null

        override suspend fun refresh(): DomainResult<Unit> {
            if (personas.value == null) personas.value = emptyList()
            return DomainResult.Success(Unit)
        }

        override suspend fun create(
            name: String,
            description: String,
            originStoryId: String?,
        ): DomainResult<Persona> {
            created += Triple(name, description, originStoryId)
            return createResult ?: DomainResult.Success(Persona("new", name, description))
        }

        override suspend fun update(
            personaId: String,
            name: String,
            description: String,
        ): DomainResult<Persona> {
            updated += Triple(personaId, name, description)
            return DomainResult.Success(Persona(personaId, name, description))
        }

        override suspend fun delete(personaId: String): DomainResult<Unit> = DomainResult.Success(Unit)

        override fun clearCreatedPersona() {
            createdPersona.value = null
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 1_000L
    }
}
