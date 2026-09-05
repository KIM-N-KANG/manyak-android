package app.manyak.common.domain.story

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StoryDetail

interface StoryRepository {
    /**
     * 스토리 상세. 회원 세션으로 조회한다 — 내가 만든 스토리는 기본 비공개라 익명 요청에는
     * 404 가 오고, 본 엔딩도 회원 집계라 토큰이 없으면 비어서 온다.
     *
     * 없는 스토리와 읽을 수 없는 스토리는 서버가 모두 404 로 돌려준다(존재 여부 비노출).
     */
    suspend fun storyDetail(storyId: String): DomainResult<StoryDetail>
}
