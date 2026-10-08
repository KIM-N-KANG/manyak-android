package app.manyak.my.persona.data.repository

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.common.entity.persona.CreatedPersona
import app.manyak.common.entity.persona.Persona
import app.manyak.my.persona.data.api.PersonaApi
import app.manyak.my.persona.data.dto.PersonaRequestDto
import app.manyak.my.persona.data.dto.toDomain
import app.manyak.my.persona.domain.PersonaRepository
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 메모리에만 둔다. 상세와 마이가 화면에 들어올 때마다 서버 목록을 다시 읽으므로 디스크에 남길 이유가 없다.
 * 회원 귀속 값이라 [UserScopedStore] 로 종료 정리에 참여한다. 남으면 다음 회원의 상세에 이전 회원의
 * 페르소나가 보인다.
 *
 * 변경이 성공하면 응답으로 목록을 먼저 고치고 서버 목록을 다시 읽는다. 다시 읽기가 실패해도 방금 만든
 * 페르소나가 목록에 있어야 상세가 미리 선택할 수 있다.
 */
@Singleton
class PersonaRepositoryImpl
    @Inject
    constructor(
        private val personaApi: PersonaApi,
    ) : PersonaRepository,
        UserScopedStore {
        private val personaState = MutableStateFlow<List<Persona>?>(null)
        private val createdState = MutableStateFlow<CreatedPersona?>(null)

        override val personas: StateFlow<List<Persona>?> = personaState.asStateFlow()

        override val createdPersona: StateFlow<CreatedPersona?> = createdState.asStateFlow()

        override val storeName: String = "personas"

        override suspend fun refresh(): DomainResult<Unit> =
            when (val result = apiCall { personaApi.personas() }) {
                is DomainResult.Success -> {
                    personaState.value = result.value.map { it.toDomain() }
                    DomainResult.Success(Unit)
                }

                is DomainResult.Failure -> result
            }

        override suspend fun create(
            name: String,
            description: String,
            originStoryId: String?,
        ): DomainResult<Persona> {
            val result = apiCall { personaApi.create(PersonaRequestDto(name, description)) }.map { it.toDomain() }
            if (result is DomainResult.Success) {
                val persona = result.value
                personaState.update { list -> list.orEmpty() + persona }
                if (originStoryId != null) createdState.value = CreatedPersona(originStoryId, persona.id)
                refresh()
            }
            return result
        }

        override suspend fun update(
            personaId: String,
            name: String,
            description: String,
        ): DomainResult<Persona> {
            val result =
                apiCall { personaApi.update(personaId, PersonaRequestDto(name, description)) }.map { it.toDomain() }
            if (result is DomainResult.Success) {
                personaState.update { list -> list?.map { if (it.id == personaId) result.value else it } }
                refresh()
            }
            return result
        }

        override suspend fun delete(personaId: String): DomainResult<Unit> {
            val result = emptyBodyApiCall { personaApi.delete(personaId) }
            if (result is DomainResult.Success) {
                personaState.update { list -> list?.filterNot { it.id == personaId } }
                createdState.update { created -> created?.takeUnless { it.personaId == personaId } }
                refresh()
            }
            return result
        }

        override fun clearCreatedPersona() {
            createdState.value = null
        }

        override suspend fun clearUserData(): Boolean {
            personaState.value = null
            createdState.value = null
            return true
        }
    }
