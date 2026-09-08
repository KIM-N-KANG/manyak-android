package app.manyak.create.entity

/**
 * 제출된 완성 요청 한 건. 편집 슬롯과 분리해 requestId 별로 보존되며, 응답을 못 받거나 프로세스가
 * 재시작해도 같은 [command] 로 복구·재전송할 수 있도록 생성 결과와 입력을 함께 담는다.
 */
data class StoryCompletionRequest(
    val command: StoryCompletionCommand,
    val generationCommand: StorylineGenerationCommand?,
    val generation: StorylineGeneration,
    val progress: CreationProgress,
    /** 제출 시각(epoch millis). 목록 정렬에만 쓴다. */
    val submittedAt: Long,
    val outcome: CompletionOutcome = CompletionOutcome.Pending,
) {
    val requestId: String get() = command.requestId
}

/** 요청의 로컬 확정 상태. 응답 유실·네트워크 오류는 [Pending] 그대로 남는다. */
sealed interface CompletionOutcome {
    data object Pending : CompletionOutcome

    data class Completed(
        val story: CompletedStory,
    ) : CompletionOutcome

    /** 서버가 확정적으로 거절했거나 FAILED 로 응답했다. 입력은 보존되어 같은 requestId 로 재시도한다. */
    data object Failed : CompletionOutcome
}
