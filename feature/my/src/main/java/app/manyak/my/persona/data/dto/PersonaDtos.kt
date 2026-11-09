package app.manyak.my.persona.data.dto

import app.manyak.common.entity.persona.Persona
import kotlinx.serialization.Serializable

/** 생성 시각과 수정 시각은 화면이 쓰지 않아 역직렬화하지 않는다. */
@Serializable
data class PersonaResponseDto(
    val id: String,
    val name: String = "",
    val description: String = "",
)

@Serializable
data class PersonaRequestDto(
    val name: String,
    val description: String,
)

fun PersonaResponseDto.toDomain(): Persona = Persona(id = id, name = name, description = description)
