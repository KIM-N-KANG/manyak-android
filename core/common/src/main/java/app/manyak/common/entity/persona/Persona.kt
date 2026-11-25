package app.manyak.common.entity.persona

/**
 * 회원이 만든 페르소나. [description] 은 페르소나를 고른 채팅에서 스토리의 주인공 설정을 통째로 대신하는 글이다.
 */
data class Persona(
    val id: String,
    val name: String,
    val description: String,
)

/** 스토리 상세에서 만든 새 페르소나. 그 상세로 돌아왔을 때 미리 선택해 둔다. */
data class CreatedPersona(
    val storyId: String,
    val personaId: String,
)

/** 서버가 받는 페르소나 수의 상한. 넘으면 409 로 거절한다. */
const val PERSONA_MAX_COUNT = 10
