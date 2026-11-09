package app.manyak.my.persona.data

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.persona.CreatedPersona
import app.manyak.common.entity.persona.Persona
import app.manyak.my.persona.data.api.PersonaApi
import app.manyak.my.persona.data.dto.PersonaRequestDto
import app.manyak.my.persona.data.dto.PersonaResponseDto
import app.manyak.my.persona.data.repository.PersonaRepositoryImpl
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PersonaRepositoryImplTest {
    @Test
    fun `상세에서 만든 페르소나는 목록에 바로 들어가고 그 상세의 미리 선택으로 남는다`() =
        runTest {
            val api = FakePersonaApi()
            val repository = PersonaRepositoryImpl(api)
            api.listFails = true

            val result = repository.create("윤해솔", "소개", originStoryId = "story-1")

            assertTrue(result is DomainResult.Success)
            assertEquals(listOf(Persona("new", "윤해솔", "소개")), repository.personas.value)
            assertEquals(CreatedPersona("story-1", "new"), repository.createdPersona.value)
        }

    @Test
    fun `미리 고른 페르소나를 지우면 미리 선택도 사라지고 로그아웃 정리는 둘 다 비운다`() =
        runTest {
            val api = FakePersonaApi()
            val repository = PersonaRepositoryImpl(api)
            repository.create("윤해솔", "소개", originStoryId = "story-1")

            repository.delete("new")
            assertNull(repository.createdPersona.value)

            repository.create("강도윤", "소개", originStoryId = "story-1")
            assertTrue(repository.clearUserData())
            assertNull(repository.personas.value)
            assertNull(repository.createdPersona.value)
        }

    private class FakePersonaApi : PersonaApi {
        private val stored = mutableListOf<PersonaResponseDto>()
        var listFails = false

        override suspend fun personas(): Response<List<PersonaResponseDto>> =
            if (listFails) Response.error(500, "".toResponseBody()) else Response.success(stored.toList())

        override suspend fun create(body: PersonaRequestDto): Response<PersonaResponseDto> {
            val created = PersonaResponseDto("new", body.name, body.description)
            stored += created
            return Response.success(created)
        }

        override suspend fun update(
            personaId: String,
            body: PersonaRequestDto,
        ): Response<PersonaResponseDto> = Response.success(PersonaResponseDto(personaId, body.name, body.description))

        override suspend fun delete(personaId: String): Response<Unit> {
            stored.removeAll { it.id == personaId }
            return Response.success(Unit)
        }
    }
}
