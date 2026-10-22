package app.manyak.chat.room.presentation.composer

import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.entity.credit.Trials
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnCostTest {
    private val policy = CreditPolicy(chatImageCost = 60)
    private val left = TrialUsage(used = 1, limit = 3)
    private val spent = TrialUsage(used = 3, limit = 3)

    @Test
    fun `턴·이미지 체험이 남고 켜면 80 에서 0`() {
        val cost = chatTurnCost(20, policy, Trials(chatTurn = left, chatImage = left), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = 0), cost)
    }

    @Test
    fun `턴·이미지 체험이 남고 끄면 20 에서 0`() {
        val cost = chatTurnCost(20, policy, Trials(chatTurn = left, chatImage = left), withRealtimeImage = false)
        assertEquals(ChatTurnCost(full = 20, discounted = 0), cost)
    }

    @Test
    fun `이미지 체험만 남고 켜면 80 에서 20`() {
        val cost = chatTurnCost(20, policy, Trials(chatTurn = spent, chatImage = left), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = 20), cost)
    }

    @Test
    fun `이미지 체험만 남고 끄면 취소선 없이 20`() {
        val cost = chatTurnCost(20, policy, Trials(chatTurn = spent, chatImage = left), withRealtimeImage = false)
        assertEquals(ChatTurnCost(full = 20, discounted = 20), cost)
        assertEquals(false, cost.isDiscounted)
    }

    @Test
    fun `무제한 체험은 무료다`() {
        val unlimited = TrialUsage(used = 100, limit = null)
        val cost = chatTurnCost(20, policy, Trials(chatTurn = unlimited, chatImage = spent), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = 60), cost)
    }

    @Test
    fun `잔여를 못 받으면 정가만 있고 적용가는 없다`() {
        assertEquals(
            ChatTurnCost(full = 20, discounted = null),
            chatTurnCost(20, policy, null, withRealtimeImage = false),
        )
        // 이미지를 합산하는데 이미지 잔여만 없어도 적용가를 확정하지 않는다.
        val cost = chatTurnCost(20, policy, Trials(chatTurn = left, chatImage = null), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 80, discounted = null), cost)
    }

    @Test
    fun `고른 모델의 가격이 정가이고 턴 체험이 남으면 모델과 무관하게 0 이다`() {
        val cost = chatTurnCost(50, policy, Trials(chatTurn = left, chatImage = spent), withRealtimeImage = true)
        assertEquals(ChatTurnCost(full = 110, discounted = 60), cost)
    }

    @Test
    fun `모델 목록을 못 받으면 숫자를 보이지 않는다`() {
        assertEquals(ChatTurnCost(null, null), chatTurnCost(null, policy, null, withRealtimeImage = false))
    }

    @Test
    fun `이미지 정책값이 없으면 켰을 때만 숫자를 보이지 않는다`() {
        val noImage = CreditPolicy()
        assertEquals(
            ChatTurnCost(null, null),
            chatTurnCost(20, noImage, Trials(chatTurn = left), withRealtimeImage = true),
        )
        assertEquals(ChatTurnCost(20, 0), chatTurnCost(20, noImage, Trials(chatTurn = left), withRealtimeImage = false))
    }

    @Test
    fun `남은 체험 횟수는 한도에서 사용량을 빼고 0 아래로 내려가지 않으며 무제한은 null`() {
        assertEquals(2L, left.remaining)
        assertEquals(0L, TrialUsage(used = 4, limit = 3).remaining)
        assertEquals(null, TrialUsage(used = 4, limit = null).remaining)
    }
}
