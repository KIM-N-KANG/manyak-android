package app.manyak.my.persona.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.persona.PERSONA_MAX_COUNT
import app.manyak.common.entity.persona.Persona
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.my.persona.domain.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaListUiState(
    /** 저장소가 든 목록. 아직 받지 못했으면 null 이다. */
    val personas: List<Persona>? = null,
    val loadFailed: Boolean = false,
    val optionsTarget: Persona? = null,
    val deleteTarget: Persona? = null,
    val isDeleting: Boolean = false,
)

sealed interface PersonaListIntent {
    data object Retry : PersonaListIntent

    data object Create : PersonaListIntent

    data class OpenOptions(
        val persona: Persona,
    ) : PersonaListIntent

    data object CloseOptions : PersonaListIntent

    data object Edit : PersonaListIntent

    data object RequestDelete : PersonaListIntent

    data object ConfirmDelete : PersonaListIntent

    data object DismissDeleteDialog : PersonaListIntent
}

sealed interface PersonaListEvent {
    data class PersonasChanged(
        val personas: List<Persona>?,
    ) : PersonaListEvent

    data class LoadFailedChanged(
        val failed: Boolean,
    ) : PersonaListEvent

    data class OptionsTargetChanged(
        val persona: Persona?,
    ) : PersonaListEvent

    data class DeleteTargetChanged(
        val persona: Persona?,
    ) : PersonaListEvent

    data object DeleteStarted : PersonaListEvent
}

sealed interface PersonaListEffect {
    data object NavigateToCreate : PersonaListEffect

    data class NavigateToEdit(
        val personaId: String,
    ) : PersonaListEffect

    data object ShowLimitReached : PersonaListEffect

    data object ShowDeleted : PersonaListEffect

    data object ShowDeleteFailed : PersonaListEffect
}

/**
 * 마이의 페르소나 관리 목록. 목록은 저장소가 들고, 여기서는 들어올 때마다 다시 읽는다. 생성이나 수정 화면에서
 * 돌아오면 저장소가 이미 고쳐 둔 목록이 그대로 보인다.
 *
 * 옵션 시트와 삭제 확인의 대상은 구성 변경에서도 남도록 여기서 든다.
 */
@HiltViewModel
class PersonaListViewModel
    @Inject
    constructor(
        private val repository: PersonaRepository,
        private val analytics: Analytics,
    ) : MviViewModel<PersonaListIntent, PersonaListUiState, PersonaListEvent, PersonaListEffect>(
            PersonaListUiState(),
        ) {
        private var loadJob: Job? = null
        private var deleteJob: Job? = null

        init {
            analytics.track(AnalyticsEvent.PersonaListViewed)
            viewModelScope.launch {
                repository.personas.collect { dispatchEvent(PersonaListEvent.PersonasChanged(it)) }
            }
            load()
        }

        override suspend fun handleIntent(intent: PersonaListIntent) {
            val state = uiState.value
            when (intent) {
                PersonaListIntent.Retry -> load()

                PersonaListIntent.Create -> {
                    analytics.track(AnalyticsEvent.PersonaListCreateButtonClicked)
                    dispatchEffect(
                        if (state.personas.orEmpty().size >= PERSONA_MAX_COUNT) {
                            PersonaListEffect.ShowLimitReached
                        } else {
                            PersonaListEffect.NavigateToCreate
                        },
                    )
                }

                is PersonaListIntent.OpenOptions ->
                    dispatchEvent(PersonaListEvent.OptionsTargetChanged(intent.persona))

                PersonaListIntent.CloseOptions -> dispatchEvent(PersonaListEvent.OptionsTargetChanged(null))

                PersonaListIntent.Edit -> {
                    val target = state.optionsTarget ?: return
                    dispatchEvent(PersonaListEvent.OptionsTargetChanged(null))
                    dispatchEffect(PersonaListEffect.NavigateToEdit(target.id))
                }

                // 시트를 닫은 뒤 확인을 연다. 둘이 겹치면 시트 위에 다이얼로그가 떠 무엇을 지우는지 흐려진다.
                PersonaListIntent.RequestDelete -> {
                    val target = state.optionsTarget ?: return
                    dispatchEvent(PersonaListEvent.OptionsTargetChanged(null))
                    dispatchEvent(PersonaListEvent.DeleteTargetChanged(target))
                }

                PersonaListIntent.ConfirmDelete -> state.deleteTarget?.let(::delete)

                PersonaListIntent.DismissDeleteDialog ->
                    if (deleteJob?.isActive != true) dispatchEvent(PersonaListEvent.DeleteTargetChanged(null))
            }
        }

        private fun load() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(PersonaListEvent.LoadFailedChanged(false))
                    val failed = repository.refresh() is DomainResult.Failure
                    dispatchEvent(PersonaListEvent.LoadFailedChanged(failed))
                }
        }

        private fun delete(persona: Persona) {
            if (deleteJob?.isActive == true) return
            deleteJob =
                viewModelScope.launch {
                    dispatchEvent(PersonaListEvent.DeleteStarted)
                    when (repository.delete(persona.id)) {
                        is DomainResult.Success -> {
                            analytics.track(AnalyticsEvent.PersonaListPersonaDeleted)
                            dispatchEffect(PersonaListEffect.ShowDeleted)
                        }

                        is DomainResult.Failure -> dispatchEffect(PersonaListEffect.ShowDeleteFailed)
                    }
                    dispatchEvent(PersonaListEvent.DeleteTargetChanged(null))
                }
        }

        override fun reduce(
            state: PersonaListUiState,
            event: PersonaListEvent,
        ): PersonaListUiState =
            when (event) {
                is PersonaListEvent.PersonasChanged -> state.copy(personas = event.personas)
                is PersonaListEvent.LoadFailedChanged -> state.copy(loadFailed = event.failed)
                is PersonaListEvent.OptionsTargetChanged -> state.copy(optionsTarget = event.persona)
                is PersonaListEvent.DeleteTargetChanged -> state.copy(deleteTarget = event.persona, isDeleting = false)
                PersonaListEvent.DeleteStarted -> state.copy(isDeleting = true)
            }
    }
