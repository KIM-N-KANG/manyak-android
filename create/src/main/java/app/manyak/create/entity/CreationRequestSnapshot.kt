package app.manyak.create.entity

/**
 * 복구 조회(`GET /stories/simple/creation-requests/{requestId}`)가 돌려주는 요청 상태.
 * `COMPLETED` 의 결과는 단계에 따라 원 POST 응답과 같은 스키마다.
 */
sealed interface CreationRequestSnapshot {
    data object Pending : CreationRequestSnapshot

    data class StorylinesReady(
        val generation: StorylineGeneration,
    ) : CreationRequestSnapshot

    data class StoryReady(
        val story: CompletedStory,
    ) : CreationRequestSnapshot

    data object Failed : CreationRequestSnapshot
}
