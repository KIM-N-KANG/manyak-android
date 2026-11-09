package app.manyak.common.domain.persona

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.persona.CreatedPersona
import app.manyak.common.entity.persona.Persona
import kotlinx.coroutines.flow.StateFlow

/**
 * 스토리 상세가 페르소나를 고르는 데 필요한 것만 연다. 생성, 수정, 삭제는 마이가 소유한다.
 *
 * 목록은 회원 귀속 값이라 앱 수명 동안 한 곳에 두고 로그아웃 정리에서 비운다.
 */
interface PersonaAccess {
    /** 마지막으로 받은 내 페르소나(응답 순서). 아직 받지 못했으면 null 이다. */
    val personas: StateFlow<List<Persona>?>

    /**
     * 스토리 상세에서 만든 새 페르소나. 메모리에만 두어 앱을 새로 띄우면 남지 않는다.
     * 사용자가 직접 다른 주인공을 고르거나 채팅을 시작하면 [clearCreatedPersona] 로 지운다.
     */
    val createdPersona: StateFlow<CreatedPersona?>

    /** 목록을 다시 읽는다. 실패해도 들고 있던 목록을 지우지 않는다. */
    suspend fun refresh(): DomainResult<Unit>

    fun clearCreatedPersona()
}
