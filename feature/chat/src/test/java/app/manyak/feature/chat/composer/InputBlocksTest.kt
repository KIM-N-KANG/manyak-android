package app.manyak.feature.chat.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBlocksTest {
    @Test
    fun `상황만 강조 마커로 감싸고 빈 블럭은 버린다`() {
        val blocks =
            listOf(
                InputBlock(1, InputBlockType.SITUATION, " 문이 열린다 "),
                InputBlock(2, InputBlockType.DIALOGUE, "  "),
                InputBlock(3, InputBlockType.DIALOGUE, "누구세요?"),
            )

        assertEquals("*문이 열린다*\n\n누구세요?", serializeInputBlocks(blocks, "\n\n"))
    }

    @Test
    fun `구분자가 쓰임에 따라 갈린다`() {
        val blocks =
            listOf(
                InputBlock(1, InputBlockType.SITUATION, "문이 열린다"),
                InputBlock(2, InputBlockType.DIALOGUE, "누구세요?"),
            )

        // 서버로 보낼 때는 빈 줄, 일반 모드로 옮길 때는 공백 한 칸이다.
        assertEquals("*문이 열린다*\n\n누구세요?", serializeInputBlocks(blocks, "\n\n"))
        assertEquals("*문이 열린다* 누구세요?", serializeInputBlocks(blocks))
    }

    @Test
    fun `강조 구간은 상황으로 나머지는 대사로 쪼갠다`() {
        val blocks = parseInputBlocks("*문이 열린다* 누구세요?")

        assertEquals(
            listOf(
                InputBlock(1, InputBlockType.SITUATION, "문이 열린다"),
                InputBlock(2, InputBlockType.DIALOGUE, "누구세요?"),
            ),
            blocks,
        )
    }

    @Test
    fun `볼드 마커는 대사 안에 그대로 남는다`() {
        // 블럭 모드에 볼드 칸이 없어서 마커를 걷으면 모드를 오갈 때마다 굵기가 사라진다.
        val blocks = parseInputBlocks("**정말** 누구세요?")

        assertEquals(listOf(InputBlock(1, InputBlockType.DIALOGUE, "**정말** 누구세요?")), blocks)
    }

    @Test
    fun `여러 줄은 줄 단위로 쪼갠다`() {
        val blocks = parseInputBlocks("*문이 열린다*\n누구세요?")

        assertEquals(
            listOf(
                InputBlock(1, InputBlockType.SITUATION, "문이 열린다"),
                InputBlock(2, InputBlockType.DIALOGUE, "누구세요?"),
            ),
            blocks,
        )
    }

    @Test
    fun `쪼갤 것이 없으면 빈 목록이다`() {
        assertEquals(emptyList<InputBlock>(), parseInputBlocks("   "))
        assertEquals(emptyList<InputBlock>(), parseInputBlocks(""))
    }

    @Test
    fun `블럭을 더하면 쓰이지 않은 id 를 받는다`() {
        val blocks = createDefaultInputBlocks().addBlock(InputBlockType.DIALOGUE)

        assertEquals(listOf(1L, 2L, 3L), blocks.map { block -> block.id })

        // 가운데를 지운 뒤 더해도 남아 있는 id 와 겹치지 않는다.
        val afterRemove = blocks.removeBlock(2).addBlock(InputBlockType.SITUATION)

        assertEquals(listOf(1L, 3L, 4L), afterRemove.map { block -> block.id })
    }

    @Test
    fun `공백만 있는 블럭은 입력으로 보지 않는다`() {
        assertFalse(createDefaultInputBlocks().hasInput())
        assertTrue(createDefaultInputBlocks().updateBlock(1, " 문 ").hasInput())
        assertFalse(createDefaultInputBlocks().updateBlock(1, "   ").hasInput())
    }
}
