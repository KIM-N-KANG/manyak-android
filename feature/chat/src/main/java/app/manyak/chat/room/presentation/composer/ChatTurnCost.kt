package app.manyak.chat.room.presentation.composer

import app.manyak.common.entity.credit.CreditPolicy
import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.entity.credit.Trials

/** 턴 체험이 적용되는 유일한 모델 코드. 다른 모델을 고르면 체험이 남아도 그 모델 가격을 낸다. */
internal const val CHAT_TRIAL_MODEL_CODE = "LITE"

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
 * 정가는 고른 모델의 턴 가격에 실시간 이미지가 켜져 있으면 이미지 비용을 더한 값이고, 적용가는 체험이 남은
 * 항목의 비용을 0 으로 뺀 값이다. 턴 체험은 [CHAT_TRIAL_MODEL_CODE] 를 골랐을 때만 적용한다.
 * 체험 잔여는 적용되는 항목에만 요구한다.
 *
 * @param turnCost 고른 모델의 턴 가격. 모델 목록을 아직 못 받았으면 null.
 * @param modelCode 고른 모델 코드.
 */
fun chatTurnCost(
    turnCost: Long?,
    modelCode: String?,
    policy: CreditPolicy?,
    trials: Trials?,
    withRealtimeImage: Boolean,
): ChatTurnCost {
    val imageCost = if (withRealtimeImage) policy?.chatImageCost else 0L
    if (turnCost == null || imageCost == null) return ChatTurnCost(full = null, discounted = null)
    val full = turnCost + imageCost

    val unknown = ChatTurnCost(full = full, discounted = null)
    val turnTrial = if (modelCode == CHAT_TRIAL_MODEL_CODE) trials?.chatTurn ?: return unknown else null
    val imageTrial = if (withRealtimeImage) trials?.chatImage ?: return unknown else null

    val discounted =
        (if (turnTrial?.isFree == true) 0L else turnCost) +
            (if (imageTrial?.isFree == true) 0L else imageCost)
    return ChatTurnCost(full = full, discounted = discounted)
}

/** 고른 모델에 적용되는 턴 체험. 체험 모델이 아니면 체험이 남아도 없다. */
internal fun Trials.chatTurnFor(modelCode: String?): TrialUsage? =
    chatTurn?.takeIf { modelCode == CHAT_TRIAL_MODEL_CODE }
