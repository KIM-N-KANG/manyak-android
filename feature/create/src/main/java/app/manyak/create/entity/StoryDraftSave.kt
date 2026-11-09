package app.manyak.create.entity

/** 서버 초안 저장 결과. 간편 제작과 일반 제작이 같은 초안 API를 쓴다. */
sealed interface StoryDraftSave {
    /** [createdAt]은 서버 초안을 처음 저장한 시각(epoch millis)이다. */
    data class Saved(
        val version: Int,
        val createdAt: Long? = null,
    ) : StoryDraftSave

    /** 다른 기기나 화면이 먼저 저장했다. [version]을 기준으로 다시 보내면 덮어쓴다. */
    data class Conflict(
        val version: Int,
    ) : StoryDraftSave

    data object LimitExceeded : StoryDraftSave

    data object TooLarge : StoryDraftSave

    data object Failed : StoryDraftSave
}
