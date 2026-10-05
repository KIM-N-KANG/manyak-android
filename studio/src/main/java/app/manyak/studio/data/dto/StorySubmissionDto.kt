package app.manyak.studio.data.dto

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.studio.entity.StorySubmission
import app.manyak.studio.entity.SubmissionStatus
import kotlinx.serialization.Serializable

@Serializable
data class StorySubmissionDto(
    val submissionId: String,
    val kind: String,
    val status: String,
    val storyId: String? = null,
    val createdAt: String? = null,
    val payload: SubmissionPayloadDto = SubmissionPayloadDto(),
    val issues: List<SubmissionIssueDto> = emptyList(),
    val imageErrors: List<SubmissionIssueDto> = emptyList(),
    val errorCode: String? = null,
)

@Serializable
data class SubmissionPayloadDto(
    val title: String = "",
    val thumbnailObjectKey: String? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class SubmissionIssueDto(
    val path: String = "",
)

internal fun StorySubmissionDto.toCardOrNull(): StorySubmission? {
    if (kind != "CREATE" || !storyId.isNullOrBlank() || submissionId.isBlank()) return null
    val cardStatus = SubmissionStatus.entries.firstOrNull { it.name == status } ?: return null
    val imageIssueCount = imageErrors.count { it.path.isNotBlank() }
    return StorySubmission(
        id = submissionId,
        status = cardStatus,
        title = payload.title.trim(),
        thumbnailUrl = payload.thumbnailUrl?.takeIf { !payload.thumbnailObjectKey.isNullOrBlank() },
        submittedAt = createdAt?.toEpochMillisOrNull(),
        issueCount = issues.count { it.path.isNotBlank() } + imageIssueCount,
        hasImageError = imageIssueCount > 0 || errorCode?.startsWith("IMAGE_") == true,
    )
}
