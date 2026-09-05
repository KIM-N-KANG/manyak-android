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
    STORY_COMPLETION,
    STORY_DRAFT,
}

/** 다른 기능이 제작 배너와 재개 진입에 사용하는 최소 정보. */
data class CreationProgressSummary(
    val stage: CreationStage,
    val resumePoint: CreationResumePoint,
) {
    val isCompleting: Boolean get() = stage == CreationStage.STORY_COMPLETION
}
