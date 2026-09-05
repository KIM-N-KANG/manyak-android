package app.manyak.create.entity

/**
 * 스토리 완성(컴파일) 명령. [requestId] 는 클라이언트가 만든 UUID 로, 같은 값으로 다시
 * 요청하면 완성된 생성은 AI 재호출·중복 과금 없이 저장된 결과를 돌려받는다.
 */
data class StoryCompletionCommand(
    val requestId: String,
    /** 스토리라인 생성 응답의 간편 제작 진행 ID. */
    val simpleCreationId: Long,
    /** 선택한 스토리라인 ID. */
    val storylineId: Long,
    /** 추천 채택분이 자유 입력보다 앞에 실린다. 합산 최대 13개·각 100자. */
    val additionalInfos: List<String>,
)

/** 완성된 스토리. 채팅 진입이 연동되기 전까지는 식별자만 쓴다. */
data class CompletedStory(
    val id: String,
    val title: String,
)
