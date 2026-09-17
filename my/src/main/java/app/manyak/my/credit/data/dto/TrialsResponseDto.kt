package app.manyak.my.credit.data.dto

import app.manyak.common.entity.credit.TrialUsage
import app.manyak.common.entity.credit.Trials
import kotlinx.serialization.Serializable

/** 서버가 내려주는 무료 체험 사용량. 항목이 빠져 오면 그 항목만 모르는 것이라 전체를 실패로 보지 않는다. */
@Serializable
data class TrialsResponseDto(
    val chatTurn: TrialUsageDto? = null,
    val chatImage: TrialUsageDto? = null,
    val storyCreation: TrialUsageDto? = null,
    val storylineGeneration: TrialUsageDto? = null,
)

/** [limit] 은 서버가 명시적으로 null 을 내려 무제한을 뜻한다. */
@Serializable
data class TrialUsageDto(
    val used: Long = 0,
    val limit: Long? = null,
)

fun TrialsResponseDto.toDomain(): Trials =
    Trials(
        chatTurn = chatTurn?.toDomain(),
        chatImage = chatImage?.toDomain(),
        storyCreation = storyCreation?.toDomain(),
        storylineGeneration = storylineGeneration?.toDomain(),
    )

private fun TrialUsageDto.toDomain(): TrialUsage = TrialUsage(used = used, limit = limit)
