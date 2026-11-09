package app.manyak.my.persona.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.persona.PersonaAccess
import app.manyak.common.entity.persona.Persona

/** 마이가 소유하는 페르소나 저장소. 성공한 변경은 목록에 바로 반영하고 서버 목록을 다시 읽는다. */
interface PersonaRepository : PersonaAccess {
    /** @param originStoryId 스토리 상세에서 왔으면 그 스토리. 성공하면 그 상세의 미리 선택으로 남긴다. */
    suspend fun create(
        name: String,
        description: String,
        originStoryId: String?,
    ): DomainResult<Persona>

    suspend fun update(
        personaId: String,
        name: String,
        description: String,
    ): DomainResult<Persona>

    suspend fun delete(personaId: String): DomainResult<Unit>
}
