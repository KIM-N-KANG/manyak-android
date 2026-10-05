package app.manyak.create.general.entity

data class GeneralStoredDraft(
    val draftId: String,
    val form: GeneralStoryForm,
    val createdAt: Long,
)
