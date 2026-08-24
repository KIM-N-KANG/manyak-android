package app.manyak.core.domain.story

/**
 * 스토리라인 생성 명령. [requestId] 는 클라이언트가 만든 UUID 로, 서버가 복구 조회와 멱등
 * 판정에 쓴다. 같은 값으로 다시 요청하면 완료된 생성은 저장된 결과를 돌려주고 실패한 생성은
 * 재실행되므로, 실패 재시도는 같은 [requestId] 를 재사용한다.
 */
data class StorylineGenerationCommand(
    val requestId: String,
    val genreTagIds: List<Long>,
    val customGenreTags: List<String>,
    val protagonist: StoryCharacterInput,
    val supportingCharacters: List<StoryCharacterInput>,
    /** 재생성이면 바로 직전 시도의 [requestId]. 최초 생성은 null 을 명시해 보낸다. */
    val parentCreationId: String?,
    val isRegenerated: Boolean,
)

/** 인물 입력. 네 항목 모두 비울 수 있고 비운 자리는 AI 가 채운다. */
data class StoryCharacterInput(
    val name: String?,
    val gender: CharacterGender?,
    val featureTagIds: List<Long>,
    val customTags: List<String>,
)

data class StorylineGeneration(
    /** 간편 제작 진행(세션) ID. 이후 완성 요청이 이 진행을 가리킨다. */
    val simpleCreationId: Long,
    val storylines: List<Storyline>,
)

data class Storyline(
    val id: Long,
    val storyline: String,
    val recommendedInfos: List<StorylineRecommendedInfo>,
)

data class StorylineRecommendedInfo(
    val id: Long,
    val text: String,
)

/** 스토리라인 평가. 보조 신호이며 선택 진행을 막지 않는다. */
enum class StorylineRating {
    GOOD,
    BAD,
}
