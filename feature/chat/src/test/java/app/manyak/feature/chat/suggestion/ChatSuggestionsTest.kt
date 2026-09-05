package app.manyak.feature.chat.suggestion

import app.manyak.common.entity.chat.UserSource
import app.manyak.feature.chat.ChatRoomTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChatSuggestionsTest {
    @Test
    fun `턴이 없으면 시작 추천을 쓰고 원본 턴을 싣지 않는다`() {
        val suggestions = chatSuggestions(lastTurn = null, suggestedInputs = listOf("문을 연다"), choicesEnabled = true)

        assertEquals(listOf("문을 연다"), suggestions.items)
        assertNull(suggestions.sourceTurnId)
    }

    @Test
    fun `턴이 있으면 마지막 턴의 선택지를 쓴다`() {
        val suggestions =
            chatSuggestions(
                lastTurn = turn(id = 7, choices = listOf("문을 닫는다")),
                suggestedInputs = listOf("문을 연다"),
                choicesEnabled = true,
            )

        // 시작 추천을 계속 쓰면 이미 지난 턴의 후보를 권하게 된다.
        assertEquals(listOf("문을 닫는다"), suggestions.items)
        assertEquals(7L, suggestions.sourceTurnId)
    }

    @Test
    fun `추천을 끄면 선택지는 사라지지만 시작 추천은 남는다`() {
        assertEquals(
            emptyList<String>(),
            chatSuggestions(turn(id = 7, choices = listOf("문을 닫는다")), listOf("문을 연다"), choicesEnabled = false).items,
        )
        assertEquals(
            listOf("문을 연다"),
            chatSuggestions(lastTurn = null, suggestedInputs = listOf("문을 연다"), choicesEnabled = false).items,
        )
    }

    @Test
    fun `공백만 있는 후보는 보낼 것으로 세지 않는다`() {
        assertTrue(ChatSuggestions(items = listOf(" ", "문을 연다")).hasCandidate)
        assertFalse(ChatSuggestions(items = listOf(" ", "\n")).hasCandidate)
    }

    @Test
    fun `채운 적이 없으면 직접 입력이다`() {
        assertEquals(SuggestionOrigin(UserSource.TYPED), composerOrigin("문을 연다", filled = null))
    }

    @Test
    fun `채운 원문을 그대로 보내면 선택이고 순번은 1부터다`() {
        val filled = FilledSuggestion(text = "문을 연다", position = 2, sourceTurnId = 7)

        assertEquals(
            SuggestionOrigin(UserSource.CHOICE, sourceTurnId = 7, choiceOrder = 3),
            composerOrigin("  문을 연다\n", filled),
        )
    }

    @Test
    fun `블럭으로 쪼갰다 이어진 형태도 그대로 보낸 것으로 본다`() {
        // 손대지 않았는데 모드 때문에 고쳐 보낸 것으로 기록되면 안 된다.
        val filled = FilledSuggestion(text = "*문이 삐걱인다* 누구세요?", position = 0, sourceTurnId = 7)

        assertEquals(
            UserSource.CHOICE,
            composerOrigin("*문이 삐걱인다*\n\n누구세요?", filled).userSource,
        )
    }

    @Test
    fun `채운 뒤 손대면 고쳐 보낸 선택이고 원본 턴은 그대로 싣는다`() {
        val filled = FilledSuggestion(text = "문을 연다", position = 0, sourceTurnId = 7)

        assertEquals(
            SuggestionOrigin(UserSource.EDITED_CHOICE, sourceTurnId = 7, choiceOrder = 1),
            composerOrigin("문을 조심스럽게 연다", filled),
        )
    }

    @Test
    fun `원본 턴이 없으면 순번도 싣지 않는다`() {
        val filled = FilledSuggestion(text = "문을 연다", position = 2, sourceTurnId = null)

        // 한쪽만 보내면 서버가 짝이 맞지 않는 기록을 남기게 된다.
        assertEquals(SuggestionOrigin(UserSource.CHOICE), composerOrigin("문을 연다", filled))
        assertEquals(SuggestionOrigin(UserSource.CHOICE), choiceOrigin(position = 2, sourceTurnId = null))
    }

    @Test
    fun `눌러서 바로 보내면 대조 없이 선택이다`() {
        assertEquals(
            SuggestionOrigin(UserSource.CHOICE, sourceTurnId = 7, choiceOrder = 1),
            choiceOrigin(position = 0, sourceTurnId = 7),
        )
    }

    @Test
    fun `즉시 전송도 블럭 전송과 같은 모양으로 정규화한다`() {
        assertEquals("*문이 삐걱인다*\n\n누구세요?", normalizeSuggestion("*문이 삐걱인다* 누구세요?"))
    }

    @Test
    fun `무작위 선택은 공백을 건너뛰고 원래 자리를 돌려준다`() {
        val items = listOf(" ", "문을 연다", "", "창문으로 나간다")
        val random = Random(1)

        val picked = List(50) { randomSuggestionPosition(items, random) }.toSet()

        assertEquals(setOf(1, 3), picked)
        assertNull(randomSuggestionPosition(listOf(" ", ""), random))
        assertNull(randomSuggestionPosition(emptyList(), random))
    }

    @Test
    fun `선택지 생성은 마지막 턴에 선택지가 없을 때만 보낸다`() {
        assertTrue(shouldGenerateChoices(turn(id = 1), choicesEnabled = true, isStreaming = false))
        assertFalse(shouldGenerateChoices(turn(id = 1), choicesEnabled = false, isStreaming = false))
        assertFalse(shouldGenerateChoices(turn(id = 1), choicesEnabled = true, isStreaming = true))
        assertFalse(shouldGenerateChoices(lastTurn = null, choicesEnabled = true, isStreaming = false))
        assertFalse(
            shouldGenerateChoices(turn(id = 1, choices = listOf("문을 닫는다")), choicesEnabled = true, isStreaming = false),
        )
    }

    private fun turn(
        id: Long,
        choices: List<String> = emptyList(),
    ): ChatRoomTurn = ChatRoomTurn(id = id, userInput = "문을 연다", aiOutput = "문이 열린다", choices = choices)
}
