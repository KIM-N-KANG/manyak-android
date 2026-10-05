package app.manyak.studio.entity

data class StorySubmission(
    val id: String,
    val status: SubmissionStatus,
    val title: String,
    val thumbnailUrl: String?,
    val submittedAt: Long?,
    val issueCount: Int,
    val hasImageError: Boolean,
)

enum class SubmissionStatus {
    PENDING,
    REJECTED,
    FAILED,
}
