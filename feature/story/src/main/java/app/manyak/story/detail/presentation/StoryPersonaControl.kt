package app.manyak.story.detail.presentation

import app.manyak.analytics.domain.Analytics
import app.manyak.analytics.entity.AnalyticsEvent
import app.manyak.analytics.entity.PersonaType
import app.manyak.common.domain.persona.PersonaAccess
import app.manyak.common.entity.persona.PERSONA_MAX_COUNT
import app.manyak.common.entity.persona.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 상세의 페르소나 셀렉트 상태. */
data class StoryPersonaUiState(
    /** 내 페르소나(응답 순서). 받지 못했으면 비어 있고 기본 주인공만 고를 수 있다. */
    val personas: List<Persona> = emptyList(),
    /** 사용자가 직접 고른 페르소나. null 이면 기본 주인공이다. */
    val pickedId: String? = null,
    /** 이 상세에서 만들고 돌아온 새 페르소나. 직접 고른 값보다 앞선다. */
    val createdId: String? = null,
) {
    /** 채팅을 시작할 주인공. 목록에 없는 값(그새 지운 페르소나)은 기본 주인공으로 돌아간다. */
    val selected: Persona?
        get() = (createdId ?: pickedId)?.let { id -> personas.firstOrNull { persona -> persona.id == id } }
}

sealed interface PersonaAction {
    /** @param personaId null 이면 기본 주인공이다. */
    data class Select(
        val personaId: String?,
    ) : PersonaAction

    /** "페르소나 생성하기". 고를 수 있는 값이 아니라 생성 화면으로 가는 동작이다. */
    data object Create : PersonaAction
}

/**
 * 상세의 페르소나 선택. 목록과 미리 선택은 저장소가, 직접 고른 값은 여기가 든다.
 *
 * 고른 값을 UiState 가 아니라 여기서 읽는 이유는 시작 설정과 같다. 상태 반영이 이벤트 채널을 거쳐 한 박자
 * 늦어서, 고른 직후 채팅 시작이 들어오면 UiState 에는 아직 이전 선택이 있다.
 */
internal class StoryPersonaControl(
    private val storyId: String,
    private val scope: CoroutineScope,
    private val access: PersonaAccess,
    private val analytics: Analytics,
    private val emit: suspend (StoryPersonaUiState) -> Unit,
    private val notify: suspend (StoryDetailEffect) -> Unit,
) {
    private var pickedId: String? = null
    private var refreshJob: Job? = null

    init {
        scope.launch {
            combine(access.personas, access.createdPersona) { _, _ -> snapshot() }.collect { emit(it) }
        }
    }

    /** 화면에 들어올 때마다 다시 읽는다. 마이에서 고치거나 지운 페르소나가 셀렉트에 남지 않게 한다. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        // 못 받으면 기본 주인공만 보이고 채팅은 그대로 시작할 수 있어 실패를 알리지 않는다.
        refreshJob = scope.launch { access.refresh() }
    }

    suspend fun handle(action: PersonaAction) {
        when (action) {
            is PersonaAction.Select -> select(action.personaId)
            PersonaAction.Create -> requestCreate()
        }
    }

    /**
     * 채팅을 시작할 주인공을 정한다. 미리 선택은 사라지고, 고른 주인공은 직접 고른 값으로 남겨 채팅방에서
     * 돌아왔을 때 그대로 보인다.
     *
     * @return 고른 페르소나. null 이면 기본 주인공이다.
     */
    suspend fun commitForChat(): String? {
        val personaId = snapshot().selected?.id
        pickedId = personaId
        access.clearCreatedPersona()
        emit(snapshot())
        return personaId
    }

    /** 지금과 같은 주인공을 다시 고르면 아무것도 하지 않는다. 직접 고르면 미리 선택은 사라진다. */
    private suspend fun select(personaId: String?) {
        if (personaId == snapshot().selected?.id) return
        analytics.track(AnalyticsEvent.PersonaSelected(storyId, personaType(personaId)))
        pickedId = personaId
        access.clearCreatedPersona()
        emit(snapshot())
    }

    private suspend fun requestCreate() {
        analytics.track(AnalyticsEvent.PersonaCreateButtonClicked(storyId))
        val count = access.personas.value?.size ?: 0
        notify(
            if (count >= PERSONA_MAX_COUNT) {
                StoryDetailEffect.ShowPersonaLimitReached
            } else {
                StoryDetailEffect.NavigateToPersonaCreate
            },
        )
    }

    private fun snapshot() =
        StoryPersonaUiState(
            personas = access.personas.value.orEmpty(),
            pickedId = pickedId,
            createdId =
                access.createdPersona.value
                    ?.takeIf { it.storyId == storyId }
                    ?.personaId,
        )
}

internal fun personaType(personaId: String?): PersonaType =
    if (personaId == null) PersonaType.DEFAULT else PersonaType.PERSONA
