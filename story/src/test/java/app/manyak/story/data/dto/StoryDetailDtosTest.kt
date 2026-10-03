package app.manyak.story.data.dto

import app.manyak.story.entity.StoryVisibility
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryDetailDtosTest {
    @Test
    fun `소개와 공개 범위를 읽고 공백 소개는 생략한다`() {
        val story =
            Json
                .decodeFromString<StoryDetailResponseDto>(
                    """{
                "id":"story","visibility":"PUBLIC","characters":[
                    {"name":"시계공","description":"시계를 고칩니다"},
                    {"name":"방문자","description":"  "}
                ]
            }""",
                ).toDomain()
        assertEquals(StoryVisibility.PUBLIC, story.visibility)
        assertEquals("시계를 고칩니다", story.characters.first().description)
        assertNull(story.characters.last().description)
        assertNull(story.characters.first().imageUrl)
        assertEquals(
            StoryVisibility.PRIVATE,
            StoryDetailResponseDto("story", visibility = "PRIVATE").toDomain().visibility,
        )
    }

    @Test
    fun `공개 범위가 없거나 알 수 없는 값이어도 상세를 읽는다`() {
        listOf("{\"id\":\"story\"}", "{\"id\":\"story\",\"visibility\":\"UNLISTED\"}").forEach { json ->
            assertNull(Json.decodeFromString<StoryDetailResponseDto>(json).toDomain().visibility)
        }
    }
}
