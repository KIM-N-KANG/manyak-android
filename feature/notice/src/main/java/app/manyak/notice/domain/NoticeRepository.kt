package app.manyak.notice.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.notice.entity.NoticeDetail
import app.manyak.notice.entity.NoticePage

interface NoticeRepository {
    /** 게시 최신순. [cursor] 가 null 이면 첫 페이지다. */
    suspend fun notices(cursor: String? = null): DomainResult<NoticePage>

    /** 미게시·삭제는 404 `DomainError.Server` 로 온다. */
    suspend fun notice(noticeId: String): DomainResult<NoticeDetail>
}
