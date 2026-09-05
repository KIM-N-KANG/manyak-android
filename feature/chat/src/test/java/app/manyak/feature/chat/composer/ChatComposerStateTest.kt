package app.manyak.feature.chat.composer

import app.manyak.common.domain.chat.ChatInputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerStateTest {
    @Test
    fun `모드를 오가도 쓰던 내용이 남는다`() {
        val plain = ChatComposerState(mode = ChatInputMode.PLAIN, plainText = "*문이 열린다* 누구세요?")

        val block = plain.convertTo(ChatInputMode.BLOCK)

        assertEquals(
            listOf(
                InputBlock(1, InputBlockType.SITUATION, "문이 열린다"),
                InputBlock(2, InputBlockType.DIALOGUE, "누구세요?"),
            ),
            block.blocks,
        )
        assertEquals("", block.plainText)

        val roundTrip = block.convertTo(ChatInputMode.PLAIN)

        assertEquals("*문이 열린다* 누구세요?", roundTrip.plainText)
        assertEquals(emptyList<InputBlock>(), roundTrip.blocks)
    }

    @Test
    fun `쪼갤 것이 없으면 기본 블럭 둘로 되돌린다`() {
        // 빈 목록으로 두면 입력할 칸이 하나도 없는 컴포저가 된다.
        val block = ChatComposerState(mode = ChatInputMode.PLAIN, plainText = "   ").convertTo(ChatInputMode.BLOCK)

        assertEquals(createDefaultInputBlocks(), block.blocks)
    }

    @Test
    fun `같은 모드로 바꾸면 아무것도 하지 않는다`() {
        val state = ChatComposerState(mode = ChatInputMode.PLAIN, plainText = "누구세요?")

        assertEquals(state, state.convertTo(ChatInputMode.PLAIN))
    }

    @Test
    fun `보낼 문장은 모드에 따라 다르게 만든다`() {
        val block =
            ChatComposerState(
                mode = ChatInputMode.BLOCK,
                blocks =
                    listOf(
                        InputBlock(1, InputBlockType.SITUATION, "문이 열린다"),
                        InputBlock(2, InputBlockType.DIALOGUE, "누구세요?"),
                    ),
            )

        assertEquals("*문이 열린다*\n\n누구세요?", block.toUserInput())
        assertEquals("누구세요?", ChatComposerState(mode = ChatInputMode.PLAIN, plainText = " 누구세요? ").toUserInput())
    }

    @Test
    fun `전송 뒤에는 비우되 모드는 그대로 둔다`() {
        val cleared = ChatComposerState(mode = ChatInputMode.PLAIN, plainText = "누구세요?").cleared()

        assertEquals(ChatInputMode.PLAIN, cleared.mode)
        assertFalse(cleared.hasInput)
    }

    @Test
    fun `스트리밍 중에는 무엇을 썼든 잠기고 스피너를 보여 준다`() {
        val state = sendButtonState(hasInput = true, hasSuggestions = true, choicesEnabled = true, isStreaming = true)

        assertFalse(state.enabled)
        assertEquals(SendButtonIcon.SPINNER, state.icon)
    }

    @Test
    fun `입력이 있으면 보내기 아이콘으로 활성된다`() {
        val state = sendButtonState(hasInput = true, hasSuggestions = false, choicesEnabled = true, isStreaming = false)

        assertTrue(state.enabled)
        assertEquals(SendButtonIcon.SEND, state.icon)
    }

    @Test
    fun `입력이 없고 추천이 켜져 있으면 무작위 전송이 된다`() {
        val state = sendButtonState(hasInput = false, hasSuggestions = true, choicesEnabled = true, isStreaming = false)

        assertTrue(state.enabled)
        assertEquals(SendButtonIcon.RANDOM, state.icon)
    }

    @Test
    fun `추천이 켜져 있지만 없으면 아이콘을 유지한 채 잠근다`() {
        // 아이콘이 오갔다 하면 무엇을 누르는 버튼인지 읽히지 않는다.
        val state =
            sendButtonState(hasInput = false, hasSuggestions = false, choicesEnabled = true, isStreaming = false)

        assertFalse(state.enabled)
        assertEquals(SendButtonIcon.RANDOM, state.icon)
    }

    @Test
    fun `추천을 꺼 두면 입력이 없을 때 보내기 아이콘으로 잠긴다`() {
        val state =
            sendButtonState(hasInput = false, hasSuggestions = true, choicesEnabled = false, isStreaming = false)

        assertFalse(state.enabled)
        assertEquals(SendButtonIcon.SEND, state.icon)
    }
}
