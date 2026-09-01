package app.manyak.core.data.api.dto

import app.manyak.core.domain.credit.CreditPolicy
import kotlinx.serialization.Serializable

/** 서버가 내려주는 이프 수치. 필드가 빠져 오면 그 수치만 모르는 것이라 전체를 실패로 보지 않는다. */
@Serializable
data class CreditPolicyResponseDto(
    val signupReward: Long? = null,
    val inviteReward: Long? = null,
    val inviteMonthlyCap: Long? = null,
    val attendanceReward: Long? = null,
    val storyCreationCost: Long? = null,
    val chatTurnCost: Long? = null,
)

fun CreditPolicyResponseDto.toDomain(): CreditPolicy =
    CreditPolicy(
        signupReward = signupReward,
        inviteReward = inviteReward,
        inviteMonthlyCap = inviteMonthlyCap,
        attendanceReward = attendanceReward,
        storyCreationCost = storyCreationCost,
        chatTurnCost = chatTurnCost,
    )
