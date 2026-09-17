package app.manyak.chat.room.presentation.composer

import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.entity.credit.Trials
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnCostTest {
    private val policy = CreditPolicy(chatTurnCost = 20, chatImageCost = 60)
    private val left = TrialUsage(used = 1, limit = 3)
    private val spent = TrialUsage(used = 3, limit = 3)

    @Test
    fun `턴·이미지 체험이 남고 켜면 80 에서 0`() {
        val cost = chatTurnCost(policy, Trials(chatTurn = left, chatImage = left), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = 0), cost)
    }

    @Test
    fun `턴·이미지 체험이 남고 끄면 20 에서 0`() {
        val cost = chatTurnCost(policy, Trials(chatTurn = left, chatImage = left), withRealtimeImage = false)
        assertEquals(ChatTurnCost(full = 20, discounted = 0), cost)
    }

    @Test
    fun `이미지 체험만 남고 켜면 80 에서 20`() {
        val cost = chatTurnCost(policy, Trials(chatTurn = spent, chatImage = left), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = 20), cost)
    }

    @Test
    fun `이미지 체험만 남고 끄면 취소선 없이 20`() {
        val cost = chatTurnCost(policy, Trials(chatTurn = spent, chatImage = left), withRealtimeImage = false)
        assertEquals(ChatTurnCost(full = 20, discounted = 20), cost)
        assertEquals(false, cost.isDiscounted)
    }

    @Test
    fun `무제한 체험은 무료다`() {
        val unlimited = TrialUsage(used = 100, limit = null)
        val cost = chatTurnCost(policy, Trials(chatTurn = unlimited, chatImage = spent), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = 60), cost)
    }

    @Test
    fun `잔여를 못 받으면 정가만 있고 적용가는 없다`() {
        assertEquals(ChatTurnCost(full = 20, discounted = null), chatTurnCost(policy, null, withRealtimeImage = false))
        // 이미지를 합산하는데 이미지 잔여만 없어도 적용가를 확정하지 않는다.
        val cost = chatTurnCost(policy, Trials(chatTurn = left, chatImage = null), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = null), cost)
    }

    @Test
    fun `이미지 정책값이 없으면 켰을 때만 숫자를 보이지 않는다`() {
        val noImage = CreditPolicy(chatTurnCost = 20)
        assertEquals(ChatTurnCost(null, null), chatTurnCost(noImage, Trials(chatTurn = left), withRealtimeImage = true))
        assertEquals(ChatTurnCost(20, 0), chatTurnCost(noImage, Trials(chatTurn = left), withRealtimeImage = false))
    }
}
