package app.manyak.my.persona.domain

import app.manyak.my.persona.entity.PersonaGender
import app.manyak.my.persona.entity.PersonaProfile

/**
 * 성별과 특징을 서버 페르소나 소개 글로 합친다. 페르소나를 고른 채팅에서는 이 글이 스토리의 주인공
 * 설정을 통째로 대신하므로 일반 제작 주인공 글과 같은 형식이어야 한다. 화면 언어와 무관한 글 형식이라
 * 성별 값도 리소스가 아니라 여기서 정한다.
 */
fun buildPersonaDescription(
    gender: PersonaGender,
    feature: String,
): String = listOf(PROTAGONIST_HEADING, GENDER_HEADING, gender.text, feature.trim()).joinToString("\n")

/** 소개 글을 성별과 특징으로 나눈다. 형식이 다른 글은 성별 없이 통째로 특징에 둬 내용을 잃지 않는다. */
fun parsePersonaDescription(description: String): PersonaProfile {
    val lines = description.trim().lines()
    val body = if (lines.firstOrNull()?.trim() == PROTAGONIST_HEADING) lines.drop(1) else lines
    val gender =
        if (body.firstOrNull()?.trim() == GENDER_HEADING) {
            PersonaGender.entries.firstOrNull { it.text == body.getOrNull(1)?.trim() }
        } else {
            null
        }
    val feature = if (gender == null) lines else body.drop(2)
    return PersonaProfile(gender = gender, feature = feature.joinToString("\n").trim())
}

/** 목록 줄의 특징 요약. 절 제목 줄을 빼고 나머지 줄을 한 줄로 잇는다. */
fun personaFeatureSummary(feature: String): String =
    feature
        .lines()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .joinToString(" ")

private const val PROTAGONIST_HEADING = "# 주인공"

private const val GENDER_HEADING = "## 성별"

private val PersonaGender.text: String
    get() =
        when (this) {
            PersonaGender.MALE -> "남성"
            PersonaGender.FEMALE -> "여성"
        }
