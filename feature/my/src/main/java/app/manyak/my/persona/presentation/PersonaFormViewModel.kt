package app.manyak.my.persona.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.my.persona.domain.PersonaRepository
import app.manyak.my.persona.domain.buildPersonaDescription
import app.manyak.my.persona.domain.parsePersonaDescription
import app.manyak.my.persona.entity.PersonaGender
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 수정 대상을 채우기 전의 상태. 생성은 처음부터 [READY] 다. */
enum class PersonaFormLoad { LOADING, FAILED, NOT_FOUND, READY }

data class PersonaFormUiState(
    val isEdit: Boolean,
    val load: PersonaFormLoad,
    val name: String = "",
    val gender: PersonaGender? = null,
    val feature: String = "",
    val nameError: Boolean = false,
    val genderError: Boolean = false,
    val featureError: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    companion object {
        const val NAME_MAX_LENGTH = 20

        /** 성별 절을 붙여도 서버 소개 상한 1,000자 안에 든다. */
        const val FEATURE_MAX_LENGTH = 500
    }
}

sealed interface PersonaFormIntent {
    data object Retry : PersonaFormIntent

    data class NameChanged(
        val name: String,
    ) : PersonaFormIntent

    data class GenderChanged(
        val gender: PersonaGender,
    ) : PersonaFormIntent

    data class FeatureChanged(
        val feature: String,
    ) : PersonaFormIntent

    data object Submit : PersonaFormIntent
}

sealed interface PersonaFormEvent {
    data class LoadChanged(
        val load: PersonaFormLoad,
    ) : PersonaFormEvent

    data class Filled(
        val name: String,
        val gender: PersonaGender?,
        val feature: String,
    ) : PersonaFormEvent

    data class NameChanged(
        val name: String,
    ) : PersonaFormEvent

    data class GenderChanged(
        val gender: PersonaGender,
    ) : PersonaFormEvent

    data class FeatureChanged(
        val feature: String,
    ) : PersonaFormEvent

    data class ValidationFailed(
        val name: Boolean,
        val gender: Boolean,
        val feature: Boolean,
    ) : PersonaFormEvent

    data class SubmittingChanged(
        val submitting: Boolean,
    ) : PersonaFormEvent
}

sealed interface PersonaFormEffect {
    /** 저장했다. 화면이 알리고 들어온 화면으로 돌아간다. */
    data class Saved(
        val isEdit: Boolean,
    ) : PersonaFormEffect

    data object ShowLimitReached : PersonaFormEffect

    data class ShowSaveFailed(
        val isEdit: Boolean,
    ) : PersonaFormEffect
}

/**
 * 페르소나 생성과 수정. 같은 폼이고 [personaId] 가 있으면 수정이다.
 *
 * 저장에 실패하면 입력을 그대로 둔다. 수정 대상은 저장소의 목록에서 찾고, 목록이 없으면 다시 읽는다. 목록에
 * 없는 ID 는 찾을 수 없는 페르소나다.
 */
@HiltViewModel(assistedFactory = PersonaFormViewModel.Factory::class)
class PersonaFormViewModel
    @AssistedInject
    constructor(
        @Assisted("personaId") private val personaId: String?,
        @Assisted("originStoryId") private val originStoryId: String?,
        private val repository: PersonaRepository,
        private val analytics: Analytics,
    ) : MviViewModel<PersonaFormIntent, PersonaFormUiState, PersonaFormEvent, PersonaFormEffect>(
            PersonaFormUiState(
                isEdit = personaId != null,
                load = if (personaId == null) PersonaFormLoad.READY else PersonaFormLoad.LOADING,
            ),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("personaId") personaId: String?,
                @Assisted("originStoryId") originStoryId: String?,
            ): PersonaFormViewModel
        }

        private var loadJob: Job? = null
        private var submitJob: Job? = null

        init {
            analytics.track(
                if (personaId == null) AnalyticsEvent.PersonaCreateViewed else AnalyticsEvent.PersonaEditViewed,
            )
            if (personaId != null) load(personaId)
        }

        override suspend fun handleIntent(intent: PersonaFormIntent) {
            when (intent) {
                PersonaFormIntent.Retry -> personaId?.let(::load)

                is PersonaFormIntent.NameChanged ->
                    dispatchEvent(
                        PersonaFormEvent.NameChanged(
                            intent.name.replace(LineBreaks, " ").take(PersonaFormUiState.NAME_MAX_LENGTH),
                        ),
                    )

                is PersonaFormIntent.GenderChanged -> dispatchEvent(PersonaFormEvent.GenderChanged(intent.gender))

                is PersonaFormIntent.FeatureChanged ->
                    dispatchEvent(
                        PersonaFormEvent.FeatureChanged(intent.feature.take(PersonaFormUiState.FEATURE_MAX_LENGTH)),
                    )

                PersonaFormIntent.Submit -> submit(uiState.value)
            }
        }

        private fun load(id: String) {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(PersonaFormEvent.LoadChanged(PersonaFormLoad.LOADING))
                    // 들고 있는 목록에 없으면 낡은 목록일 수 있어 한 번 다시 읽고 판정한다.
                    var persona = repository.personas.value?.firstOrNull { it.id == id }
                    var failed = false
                    if (persona == null) {
                        failed = repository.refresh() is DomainResult.Failure
                        persona = repository.personas.value?.firstOrNull { it.id == id }
                    }
                    when {
                        persona != null -> {
                            val profile = parsePersonaDescription(persona.description)
                            dispatchEvent(PersonaFormEvent.Filled(persona.name, profile.gender, profile.feature))
                        }

                        failed -> dispatchEvent(PersonaFormEvent.LoadChanged(PersonaFormLoad.FAILED))
                        else -> dispatchEvent(PersonaFormEvent.LoadChanged(PersonaFormLoad.NOT_FOUND))
                    }
                }
        }

        private suspend fun submit(state: PersonaFormUiState) {
            if (state.load != PersonaFormLoad.READY || submitJob?.isActive == true) return
            val name = state.name.trim()
            val feature = state.feature.trim()
            val gender = state.gender
            if (name.isEmpty() || gender == null || feature.isEmpty()) {
                dispatchEvent(PersonaFormEvent.ValidationFailed(name.isEmpty(), gender == null, feature.isEmpty()))
                return
            }
            val isEdit = personaId != null
            analytics.track(
                if (isEdit) AnalyticsEvent.PersonaEditFormSubmitted else AnalyticsEvent.PersonaCreateFormSubmitted,
            )
            dispatchEvent(PersonaFormEvent.SubmittingChanged(true))
            submitJob =
                viewModelScope.launch {
                    val description = buildPersonaDescription(gender, feature)
                    val result =
                        if (personaId != null) {
                            repository.update(personaId, name, description)
                        } else {
                            repository.create(name, description, originStoryId)
                        }
                    when (result) {
                        // 화면이 곧 사라지므로 잠금은 풀지 않는다. 풀면 나가는 동안 버튼이 되살아난다.
                        is DomainResult.Success -> {
                            analytics.track(
                                if (isEdit) {
                                    AnalyticsEvent.PersonaEditCompleted
                                } else {
                                    AnalyticsEvent.PersonaCreateCompleted
                                },
                            )
                            dispatchEffect(PersonaFormEffect.Saved(isEdit))
                        }

                        is DomainResult.Failure -> {
                            dispatchEvent(PersonaFormEvent.SubmittingChanged(false))
                            dispatchEffect(
                                if (!isEdit && result.error.isLimitReached()) {
                                    PersonaFormEffect.ShowLimitReached
                                } else {
                                    PersonaFormEffect.ShowSaveFailed(isEdit)
                                },
                            )
                        }
                    }
                }
        }

        override fun reduce(
            state: PersonaFormUiState,
            event: PersonaFormEvent,
        ): PersonaFormUiState =
            when (event) {
                is PersonaFormEvent.LoadChanged -> state.copy(load = event.load)

                is PersonaFormEvent.Filled ->
                    state.copy(
                        load = PersonaFormLoad.READY,
                        name = event.name,
                        gender = event.gender,
                        feature = event.feature,
                    )

                // 칸을 고치면 그 칸의 오류만 지운다. 기본 정보는 이름 오류가 걷히면 성별 오류가 드러난다.
                is PersonaFormEvent.NameChanged -> state.copy(name = event.name, nameError = false)

                is PersonaFormEvent.GenderChanged -> state.copy(gender = event.gender, genderError = false)

                is PersonaFormEvent.FeatureChanged -> state.copy(feature = event.feature, featureError = false)

                is PersonaFormEvent.ValidationFailed ->
                    state.copy(nameError = event.name, genderError = event.gender, featureError = event.feature)

                is PersonaFormEvent.SubmittingChanged -> state.copy(isSubmitting = event.submitting)
            }
    }

private fun DomainError.isLimitReached(): Boolean = this is DomainError.Server && status == HTTP_CONFLICT

private const val HTTP_CONFLICT = 409

private val LineBreaks = Regex("[\\r\\n\\t]")
