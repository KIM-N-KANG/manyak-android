package app.manyak.notice.domain

import kotlinx.coroutines.flow.Flow

/**
 * 상세를 열어 읽은 공지 ID. 서버에 읽음 기록이 없어 기기에만 둔다 — 회원이 아니라 기기 귀속이라
 * 로그아웃 정리 대상이 아니고, 다른 기기와 동기화하지 않는다.
 */
interface NoticeReadRepository {
    /** 최근에 읽은 순서. 읽기 실패는 빈 목록이다. */
    val readIds: Flow<List<String>>

    suspend fun markRead(noticeId: String)
}
