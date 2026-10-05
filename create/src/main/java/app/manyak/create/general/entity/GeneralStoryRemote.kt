package app.manyak.create.general.entity

data class GeneralStoryEditor(
    val content: GeneralStoryContent,
    val submission: GeneralSubmission? = null,
    val storyId: String? = null,
    val kind: String? = null,
)

data class GeneralSubmission(
    val id: String,
    val status: String,
    val issues: List<GeneralModerationIssue> = emptyList(),
    val errorCode: String? = null,
    val imageErrors: List<GeneralImageError> = emptyList(),
) {
    val canResubmit: Boolean get() = status == "REJECTED" || status == "FAILED"
}

data class GeneralModerationIssue(
    val path: String,
    val type: String,
    val rule: String,
    val reason: String,
)

data class GeneralImageError(
    val path: String,
    val errorCode: String,
)

sealed interface GeneralStorySaveResult {
    data class Accepted(
        val submissionId: String,
    ) : GeneralStorySaveResult

    data class Updated(
        val editor: GeneralStoryEditor,
    ) : GeneralStorySaveResult
}

enum class GeneralImageKind { COVER, CHARACTER }
