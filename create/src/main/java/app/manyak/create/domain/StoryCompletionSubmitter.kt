package app.manyak.create.domain

import app.manyak.create.entity.StoryCompletionRequest

/** 완성 요청을 영속하고 화면 수명과 무관한 실행자에 전송을 넘긴다. */
interface StoryCompletionSubmitter {
    /** 영속에 성공했을 때만 true 이며, 그때만 전송이 시작된다. 서버 응답은 기다리지 않는다. */
    suspend fun submit(request: StoryCompletionRequest): Boolean
}
