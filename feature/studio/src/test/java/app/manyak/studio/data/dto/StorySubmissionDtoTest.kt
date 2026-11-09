package app.manyak.studio.data.dto

import app.manyak.studio.entity.SubmissionStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorySubmissionDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `서버 필드를 읽어 반려 사유 수와 등록 시각과 표지를 표시한다`() {
        val dto =
            json.decodeFromString<StorySubmissionDto>(
                """{
                "submissionId":"s1","kind":"CREATE","status":"REJECTED","storyId":null,
                "createdAt":"2026-10-05T01:02:03Z",
                "payload":{"title":"  테스트  ","thumbnailObjectKey":"uploads/cover.webp",
                    "thumbnailUrl":"https://cdn.manyak.app/cover.webp","description":"입력 원문"},
                "issues":[{"path":"title","reason":"사유"},{"path":"description"}],
                "imageErrors":[{"path":"characters[0].images[0]","errorCode":"IMAGE_INVALID"}],
                "updatedAt":"2026-10-05T01:02:04Z"
            }""",
            )
        val card = requireNotNull(dto.toCardOrNull())
        assertEquals(SubmissionStatus.REJECTED, card.status)
        assertEquals("테스트", card.title)
        assertEquals(3, card.issueCount)
        assertTrue(card.hasImageError)
        assertEquals("https://cdn.manyak.app/cover.webp", card.thumbnailUrl)
        assertEquals(1_791_162_123_000L, card.submittedAt)
    }

    @Test
    fun `수정 제출본 승인 알 수 없는 상태와 스토리가 있는 제출본을 제외한다`() {
        val dto = StorySubmissionDto("s1", "CREATE", "PENDING")
        assertNull(dto.copy(kind = "UPDATE").toCardOrNull())
        assertNull(dto.copy(kind = "FUTURE").toCardOrNull())
        assertNull(dto.copy(status = "APPROVED").toCardOrNull())
        assertNull(dto.copy(status = "FUTURE").toCardOrNull())
        assertNull(dto.copy(storyId = "story").toCardOrNull())
        assertNull(dto.copy(submissionId = " ").toCardOrNull())
    }

    @Test
    fun `이미지 오류 코드와 빠진 날짜를 처리하며 알 수 없는 사유는 세지 않는다`() {
        val dto =
            StorySubmissionDto(
                "s1",
                "CREATE",
                "FAILED",
                createdAt = "invalid",
                errorCode = "IMAGE_DOWNLOAD_FAILED",
                issues = listOf(SubmissionIssueDto("")),
            )
        val card = requireNotNull(dto.toCardOrNull())
        assertTrue(card.hasImageError)
        assertEquals(0, card.issueCount)
        assertNull(card.submittedAt)
        assertFalse(requireNotNull(dto.copy(errorCode = "TEMPORARY_FAILURE").toCardOrNull()).hasImageError)
    }
}
