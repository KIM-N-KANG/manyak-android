package app.manyak.my.persona.entity

enum class PersonaGender { MALE, FEMALE }

/** 페르소나 소개 글을 입력 칸으로 나눈 것. 형식이 다른 글이면 [gender] 가 null 이고 글 전체가 [feature] 다. */
data class PersonaProfile(
    val gender: PersonaGender?,
    val feature: String,
)
