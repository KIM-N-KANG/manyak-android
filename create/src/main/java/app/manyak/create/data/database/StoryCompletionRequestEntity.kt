package app.manyak.create.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.CompletionOutcome
import app.manyak.create.entity.StoryCompletionRequest

/**
 * 제출된 완성 요청 한 행. 편집 슬롯과 달리 requestId 별로 여러 행이 공존하며, 결과 반영과 삭제는
 * 항상 requestId 단위다. 중첩 구조는 편집 슬롯과 같은 JSON 인코더를 쓴다.
 */
@Entity(tableName = "story_completion_request")
data class StoryCompletionRequestEntity(
    @PrimaryKey val requestId: String,
    val completionCommand: String,
    val generationCommand: String? = null,
    val generation: String,
    val progress: String,
    val submittedAt: Long,
    val status: String,
    val storyId: String? = null,
    val storyTitle: String? = null,
    /** 요청을 제출한 회원의 공개 ID. 로그아웃해도 남기고 같은 회원에게만 보인다. 빈 값은 이전 버전 행이다. */
    @ColumnInfo(defaultValue = "") val ownerId: String = UNOWNED,
) {
    companion object {
        const val STATUS_PENDING: String = "PENDING"
        const val STATUS_COMPLETED: String = "COMPLETED"
        const val STATUS_FAILED: String = "FAILED"
        const val UNOWNED: String = ""
    }
}

internal fun StoryCompletionRequest.toEntity(ownerId: String): StoryCompletionRequestEntity =
    StoryCompletionRequestEntity(
        ownerId = ownerId,
        requestId = requestId,
        completionCommand = encode(command.toDto()),
        generationCommand = generationCommand?.let { encode(it.toDto()) },
        generation = encode(generation.toDto()),
        progress = encode(progress.toDto()),
        submittedAt = submittedAt,
        status =
            when (outcome) {
                CompletionOutcome.Pending -> StoryCompletionRequestEntity.STATUS_PENDING
                is CompletionOutcome.Completed -> StoryCompletionRequestEntity.STATUS_COMPLETED
                CompletionOutcome.Failed -> StoryCompletionRequestEntity.STATUS_FAILED
            },
        storyId = (outcome as? CompletionOutcome.Completed)?.story?.id,
        storyTitle = (outcome as? CompletionOutcome.Completed)?.story?.title,
    )

/** 명령이나 생성 결과를 해석할 수 없으면 null — 재전송할 재료가 없어 화면에 올릴 수 없다. 행은 지우지 않는다. */
internal fun StoryCompletionRequestEntity.toDomainOrNull(): StoryCompletionRequest? {
    val command = decodeOrNull<CompletionCommandDto>(completionCommand) ?: return null
    val snapshot = decodeOrNull<GenerationSnapshotDto>(generation) ?: return null
    val outcome =
        when (status) {
            StoryCompletionRequestEntity.STATUS_COMPLETED ->
                storyId?.let { CompletionOutcome.Completed(CompletedStory(id = it, title = storyTitle.orEmpty())) }
                    ?: CompletionOutcome.Pending

            StoryCompletionRequestEntity.STATUS_FAILED -> CompletionOutcome.Failed
            else -> CompletionOutcome.Pending
        }
    return StoryCompletionRequest(
        command = command.toDomain(),
        generationCommand = decodeOrNull<GenerationCommandDto>(generationCommand)?.toDomain(),
        generation = snapshot.toDomain(),
        progress = (decodeOrNull<ProgressDto>(progress) ?: ProgressDto()).toDomain(),
        submittedAt = submittedAt,
        outcome = outcome,
    )
}
