package app.manyak.common.entity.story

/** 재개 진입이 쌓아야 할 퍼널 단계. 라우트 구성은 앱 계층의 몫이다. */
sealed interface CreationResumePoint {
    data object KeywordStep : CreationResumePoint

    data object StorylineStep : CreationResumePoint

    data class AdditionalInfoStep(
        val storylineIndex: Int,
    ) : CreationResumePoint
}

enum class CreationStage {
    KEYWORD_DRAFT,
    STORYLINE_GENERATION,
    STORY_DRAFT,
}

/** 다른 기능이 편집 초안 카드와 재개 진입에 사용하는 최소 정보. */
data class CreationProgressSummary(
    /** 초안을 만든 퍼널 세션의 ID. 재개 진입이 라우트에 싣고 삭제의 대상이 된다. */
    val draftId: String,
    val stage: CreationStage,
    val resumePoint: CreationResumePoint,
    /** 처음 임시 저장한 시각(epoch millis). 기능 이전에 저장한 초안은 null 이다. */
    val createdAt: Long? = null,
)

enum class CompletionRequestStatus {
    PENDING,
    COMPLETED,
    FAILED,
}

/** 제작 탭이 완성 요청 카드를 그리는 데 쓰는 최소 정보. 입력 원문은 제작 기능이 소유한다. */
data class CompletionRequestSummary(
    val requestId: String,
    val submittedAt: Long,
    val status: CompletionRequestStatus,
    /** 완료된 요청의 실제 스토리 ID. 목록의 일반 카드로 바꿔 끼우는 열쇠다. */
    val storyId: String? = null,
    val storyTitle: String? = null,
    /** 제출한 초안을 처음 임시 저장한 시각(epoch millis). 기능 이전 요청은 null 이다. */
    val createdAt: Long? = null,
)
