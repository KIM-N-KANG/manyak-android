package app.manyak.my.persona.domain

import app.manyak.my.persona.entity.PersonaGender
import app.manyak.my.persona.entity.PersonaProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonaDescriptionTest {
    /** 페르소나를 고른 채팅에서 이 글이 주인공 설정을 통째로 대신하므로 웹과 같은 형식이어야 한다. */
    @Test
    fun `소개 글은 주인공 제목과 성별 절 아래 특징을 둔다`() {
        assertEquals(
            "# 주인공\n## 성별\n여성\n## 성격\n겁이 많다",
            buildPersonaDescription(PersonaGender.FEMALE, "  ## 성격\n겁이 많다 \n"),
        )
    }

    @Test
    fun `만든 형식의 글은 성별과 특징으로 나눈다`() {
        val description = buildPersonaDescription(PersonaGender.MALE, "## 말투\n존댓말")

        assertEquals(PersonaProfile(PersonaGender.MALE, "## 말투\n존댓말"), parsePersonaDescription(description))
    }

    @Test
    fun `형식이 다른 글은 성별 없이 통째로 특징에 둔다`() {
        assertEquals(PersonaProfile(null, "그냥 적은 소개"), parsePersonaDescription("그냥 적은 소개"))
        assertEquals(
            PersonaProfile(null, "# 주인공\n## 성별\n미정\n특징"),
            parsePersonaDescription("# 주인공\n## 성별\n미정\n특징"),
        )
    }

    @Test
    fun `요약은 절 제목 줄을 빼고 한 줄로 잇는다`() {
        assertEquals("겁이 많다 존댓말을 쓴다", personaFeatureSummary("## 성격\n겁이 많다\n\n## 말투\n존댓말을 쓴다"))
    }
}
