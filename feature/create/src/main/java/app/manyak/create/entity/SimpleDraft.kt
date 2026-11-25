package app.manyak.create.entity

import app.manyak.common.entity.story.CreationStage

/** 서버 간편 제작 초안을 연 결과. */
sealed interface SimpleDraftOpen {
    /** [createdAt]은 서버 초안을 처음 저장한 시각(epoch millis)이다. */
    data class Found(
        val record: PendingStoryCreation,
        val version: Int,
        val createdAt: Long? = null,
    ) : SimpleDraftOpen

    /**
     * 초안이 없거나(다른 곳에서 완성하거나 지움) 이어 갈 생성 결과를 받을 수 없다. 결과가 없는 초안이 서버에 남아 있으면
     * [version]으로 덮어써 같은 ID로 새로 시작한다.
     */
    data class NotFound(
        val version: Int? = null,
    ) : SimpleDraftOpen
}

/** 제작 탭에 그리는 서버 간편 제작 초안 카드. */
data class SimpleServerDraft(
    val draftId: String,
    val stage: CreationStage,
    /** 추가 정보 단계의 초안이면 고른 스토리라인 본문. */
    val oneLineIntro: String?,
    val version: Int,
    val createdAt: Long?,
)
