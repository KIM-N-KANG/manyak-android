package app.manyak.create.general.entity

/** 제작 탭 카드에 그리는 일반 제작 임시 저장본. 서버 초안과 아직 옮기지 못한 기기 안 저장본이 섞여 있다. */
data class GeneralStoredDraft(
    val draftId: String,
    val createdAt: Long?,
    val title: String?,
    val oneLineIntro: String?,
    val thumbnailUrl: String?,
)

sealed interface GeneralDraftOpen {
    /** 서버로 옮기지 못하고 기기에 남은 저장본. 서버에 처음 저장하면 기기에서 지운다. */
    data class Local(
        val form: GeneralStoryForm,
    ) : GeneralDraftOpen

    data class Server(
        val form: GeneralStoryForm,
        val version: Int,
    ) : GeneralDraftOpen

    data object NotFound : GeneralDraftOpen
}
