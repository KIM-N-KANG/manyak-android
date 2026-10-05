package app.manyak.create.general.presentation

import app.manyak.create.general.domain.generalModerationTarget
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralSubmission
import app.manyak.create.general.entity.GeneralTab

data class GeneralReviewNotice(
    val tab: GeneralTab?,
    val message: GeneralReviewMessage,
    val target: GeneralFieldTarget? = null,
)

internal data class GeneralReviewErrors(
    val fields: Map<GeneralFieldTarget, GeneralReviewMessage>,
    val notices: List<GeneralReviewNotice>,
)

internal fun generalReviewErrors(
    submission: GeneralSubmission,
    form: GeneralStoryForm,
): GeneralReviewErrors {
    val fields = linkedMapOf<GeneralFieldTarget, GeneralReviewMessage>()
    val notices = mutableListOf<GeneralReviewNotice>()

    fun add(
        path: String,
        message: GeneralReviewMessage,
    ) {
        val target = generalModerationTarget(path, form)
        if (target == null || target.field == GeneralField.ITEMS) {
            notices += GeneralReviewNotice(target?.tab, message, target)
        }
        if (target != null) fields.putIfAbsent(target, message)
    }
    submission.issues.forEach {
        add(it.path, GeneralReviewMessage(it.reason.takeIf(String::isNotBlank), GeneralEditorMessage.UNKNOWN_ISSUE))
    }
    submission.imageErrors.forEach { error ->
        val message =
            when (error.errorCode) {
                "IMAGE_INVALID" -> GeneralEditorMessage.IMAGE_INVALID
                "IMAGE_UNREADABLE" -> GeneralEditorMessage.IMAGE_UNREADABLE
                else -> GeneralEditorMessage.IMAGE_ERROR
            }
        add(error.path, GeneralReviewMessage(message = message))
    }
    return GeneralReviewErrors(fields.entries.sortedBy { it.key.tab.ordinal }.associate { it.toPair() }, notices)
}
