package app.manyak.home.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.story.StorySummary

interface HomeRepository {
    /**
     * 마냑 공식 계정의 오리지널 스토리 목록. 서버 등록순을 그대로 유지하며, 공식 계정이 설정되지
     * 않은 환경은 빈 목록이다.
     */
    suspend fun originalStories(): DomainResult<List<StorySummary>>
}
