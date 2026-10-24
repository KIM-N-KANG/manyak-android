package app.manyak.notice.data.repository

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.network.data.api.apiCall
import app.manyak.notice.data.api.NoticeApi
import app.manyak.notice.data.api.dto.NoticeDetailDto
import app.manyak.notice.data.api.dto.NoticeItemDto
import app.manyak.notice.data.api.dto.NoticePageDto
import app.manyak.notice.domain.NoticeRepository
import app.manyak.notice.entity.NoticeDetail
import app.manyak.notice.entity.NoticePage
import app.manyak.notice.entity.NoticeSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoticeRepositoryImpl
    @Inject
    constructor(
        private val api: NoticeApi,
    ) : NoticeRepository {
        override suspend fun notices(cursor: String?): DomainResult<NoticePage> =
            apiCall { api.list(cursor) }.map { it.toEntity() }

        override suspend fun notice(noticeId: String): DomainResult<NoticeDetail> =
            apiCall { api.detail(noticeId) }.map { it.toEntity() }
    }

private fun NoticePageDto.toEntity() = NoticePage(items = items.map { it.toEntity() }, nextCursor = nextCursor)

private fun NoticeItemDto.toEntity() =
    NoticeSummary(id = noticeId, title = title, publishedAtMillis = publishedAt?.toEpochMillisOrNull())

private fun NoticeDetailDto.toEntity() =
    NoticeDetail(
        id = noticeId,
        title = title,
        body = body,
        publishedAtMillis = publishedAt?.toEpochMillisOrNull(),
        updatedAtMillis = updatedAt?.toEpochMillisOrNull(),
    )
