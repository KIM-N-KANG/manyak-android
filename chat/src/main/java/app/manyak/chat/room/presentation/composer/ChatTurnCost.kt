package app.manyak.chat.room.presentation.composer

import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.credit.Trials

/**
 * 전송 버튼 옆 배지에 보일 턴 비용.
 *
 * @property full 체험 없이 낼 정가. 필요한 정책값을 못 받았으면 null.
 * @property discounted 남은 체험을 적용한 실제 비용. 정가나 잔여를 못 받았으면 null.
 */
data class ChatTurnCost(
    val full: Long?,
    val discounted: Long?,
) {
    /** 체험이 남아 정가보다 싸졌는지. 그때만 정가에 취소선을 긋는다. */
    val isDiscounted: Boolean
        get() = full != null && discounted != null && discounted < full
}

/**
 * 고급 모델의 턴 비용. 서버 정책 응답에 고급 비용이 생기기 전까지 쓰는 임시 값이며, AI 의 고급 등급
 * 실험 조건과 같다.
 */
internal const val ADVANCED_CHAT_TURN_COST = 50L

/**
 * 정가는 턴 비용에 실시간 이미지가 켜져 있으면 이미지 비용을 더한 값이고, 적용가는 체험이 남은 항목의
 * 비용을 0 으로 뺀 값이다. 이미지 항목의 정책값·잔여는 실시간 이미지를 합산할 때만 요구한다.
 * 고급 모델은 턴 비용이 [ADVANCED_CHAT_TURN_COST] 이고 턴 체험을 적용하지 않는다.
 */
fun chatTurnCost(
    policy: CreditPolicy?,
    trials: Trials?,
    withRealtimeImage: Boolean,
    advancedModel: Boolean = false,
): ChatTurnCost {
    val turnCost = if (advancedModel) ADVANCED_CHAT_TURN_COST else policy?.chatTurnCost
    val imageCost = if (withRealtimeImage) policy?.chatImageCost else 0L
    if (turnCost == null || imageCost == null) return ChatTurnCost(full = null, discounted = null)
    val full = turnCost + imageCost

    val turnTrial = trials?.chatTurn
    val imageTrial = trials?.chatImage
    if (turnTrial == null || (withRealtimeImage && imageTrial == null)) {
        return ChatTurnCost(full = full, discounted = null)
    }

    val discounted =
        (if (!advancedModel && turnTrial.isFree) 0L else turnCost) +
            (if (imageTrial?.isFree == true) 0L else imageCost)
    return ChatTurnCost(full = full, discounted = discounted)
}
