package app.manyak.notice.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.notice.domain.NoticeReadRepository
import app.manyak.notice.domain.NoticeRepository
import app.manyak.notice.domain.withRead
import app.manyak.notice.entity.NoticeDetail
import app.manyak.notice.entity.NoticePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeNoticeRepository(
    var pages: Map<String?, DomainResult<NoticePage>> = emptyMap(),
    var details: Map<String, DomainResult<NoticeDetail>> = emptyMap(),
) : NoticeRepository {
    val listCalls = mutableListOf<String?>()

    override suspend fun notices(cursor: String?): DomainResult<NoticePage> {
        listCalls += cursor
        return pages[cursor] ?: DomainResult.Failure(DomainError.Network)
    }

    override suspend fun notice(noticeId: String): DomainResult<NoticeDetail> =
        details[noticeId] ?: DomainResult.Failure(DomainError.Network)
}

internal class FakeNoticeReadRepository(
    initial: List<String> = emptyList(),
) : NoticeReadRepository {
    val ids = MutableStateFlow(initial)
    override val readIds: Flow<List<String>> = ids

    override suspend fun markRead(noticeId: String) {
        ids.value = ids.value.withRead(noticeId)
    }
}

internal fun notFound() =
    DomainResult.Failure(DomainError.Server(status = 404, code = "NOTICE_NOT_FOUND", requestId = null))
