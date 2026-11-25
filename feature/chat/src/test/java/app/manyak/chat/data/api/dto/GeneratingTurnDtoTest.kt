package app.manyak.chat.data.api.dto

import app.manyak.chat.entity.GeneratingTurn
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratingTurnDtoTest {
    @Test
    fun `재생성과 입력 수정만 대상 턴을 싣고 입력 수정은 뒤 턴을 숨긴다`() {
        assertEquals(GeneratingTurn("gen", "", targetTurnId = 7, isEdit = false), mapped("REGENERATE", 7))
        assertEquals(GeneratingTurn("gen", "", targetTurnId = 7, isEdit = true), mapped("EDIT", 7))
        assertEquals(GeneratingTurn("gen", "", targetTurnId = null, isEdit = false), mapped("CONTINUE", 7))
    }

    @Test
    fun `대상 턴이 없는 입력 수정은 이어쓰기처럼 목록 끝에 그린다`() {
        assertEquals(GeneratingTurn("gen", "", targetTurnId = null, isEdit = false), mapped("EDIT", null))
    }

    private fun mapped(
        type: String,
        targetTurnId: Long?,
    ): GeneratingTurn? =
        ChatDetailResponseDto(
            id = "chat-1",
            storyId = "story-1",
            generatingTurn = GeneratingTurnDto(generationId = "gen", type = type, targetTurnId = targetTurnId),
        ).toDomain().generatingTurn
}
