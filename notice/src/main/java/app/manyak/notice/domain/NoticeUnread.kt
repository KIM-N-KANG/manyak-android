package app.manyak.notice.domain

import app.manyak.notice.entity.NoticeSummary

/** 게시된 지 이 일수 안인 공지만 안 읽음으로 센다. 오래된 공지는 열지 않아도 조용해진다. */
const val NOTICE_UNREAD_WINDOW_DAYS = 30

/** 읽음 목록에 남기는 공지 ID 상한. 넘치면 오래 전에 읽은 것부터 버린다. */
const val NOTICE_READ_IDS_LIMIT = 100

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * 게시 30일 안이고 읽은 적이 없으면 안 읽음이다. 목록만 보는 것은 읽음이 아니라서 이 판정은
 * 헤더 버튼의 점, 마이 메뉴의 배지, 목록 줄 제목 끝의 점이 모두 같이 쓴다.
 */
fun NoticeSummary.isUnread(
    readIds: Collection<String>,
    nowMillis: Long,
): Boolean {
    val published = publishedAtMillis ?: return false
    if (id in readIds) return false
    return nowMillis - published <= NOTICE_UNREAD_WINDOW_DAYS * DAY_MILLIS
}

fun List<NoticeSummary>.hasUnread(
    readIds: Collection<String>,
    nowMillis: Long,
): Boolean = any { it.isUnread(readIds, nowMillis) }

/** 방금 읽은 ID 를 맨 앞에 둔다. 이미 있으면 앞으로 옮기고 상한을 넘기면 뒤를 자른다. */
fun List<String>.withRead(noticeId: String): List<String> =
    (listOf(noticeId) + filter { it != noticeId }).take(NOTICE_READ_IDS_LIMIT)
