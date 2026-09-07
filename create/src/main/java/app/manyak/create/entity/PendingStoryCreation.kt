package app.manyak.create.entity

/**
 * 임시 저장·복원 재료가 되는 퍼널 진행 스냅숏. 키워드 입력은 저장 범위 밖이라 담지 않는다.
 */
data class CreationProgress(
    /** 추가 정보 단계로 넘긴 스토리라인 순번. 아직 선택하지 않았으면 null. */
    val selectedStorylineIndex: Int? = null,
    /** 스토리라인 선택 화면의 활성 탭 순번. */
    val activeStorylineIndex: Int = 0,
    /** 자유 텍스트 입력값. 빈 입력 자리도 그대로 담아 편집 상태를 복원한다. */
    val additionalInfoInputs: List<String> = emptyList(),
    /** 추천 추가 정보의 선택 텍스트. 자유 텍스트와 분리해 토글 상태 그대로 복원한다. */
    val selectedRecommendations: List<String> = emptyList(),
)

/**
 * 단일 슬롯에 저장되는 간편 제작 진행 레코드. 응답을 못 받은 생성·완성 요청의 복구 조회와
 * 이탈 시 임시 저장이 같은 슬롯을 쓰므로, 새 생성을 시작하면 이전 레코드는 자연히 덮인다.
 */
sealed interface PendingStoryCreation {
    /** 스토리라인 생성 요청을 보냈고 결과를 아직 화면에 반영하지 못했다. */
    data class GeneratingStorylines(
        val command: StorylineGenerationCommand,
    ) : PendingStoryCreation

    /** 완성 요청을 보냈고 채팅 진입까지 끝나지 않았다. 재진입 복원을 위해 진행 컨텍스트를 함께 담는다. */
    data class CompletingStory(
        val generationCommand: StorylineGenerationCommand?,
        val generation: StorylineGeneration,
        val command: StoryCompletionCommand,
        val progress: CreationProgress,
    ) : PendingStoryCreation

    /** 생성 성공 직후와 이후 편집 변경에 맞춰 갱신되는 임시 저장본. */
    data class Draft(
        val generationCommand: StorylineGenerationCommand?,
        val generation: StorylineGeneration,
        val progress: CreationProgress,
        /** 같은 페이로드의 완성 재시도가 requestId 를 재사용하도록 남기는 마지막 완성 명령. */
        val lastCompletionCommand: StoryCompletionCommand? = null,
    ) : PendingStoryCreation

    /**
     * 키워드 단계에서 편집 중 저장한 입력 스냅숏.
     *
     * 공용 계약은 키워드 단계를 저장 범위 밖에 두지만 앱은 확장해 재개를 지원한다. 복원할 AI
     * 생성 결과가 없다는 점이 다른 스테이지와 구분되므로 별도 변형으로 둔다 — 기존 세 스테이지의
     * "생성 결과가 반드시 있다"는 불변식을 깨지 않기 위해서다.
     */
    data class KeywordDraft(
        val snapshot: KeywordDraftSnapshot,
    ) : PendingStoryCreation
}
