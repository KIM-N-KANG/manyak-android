package app.manyak.notice.presentation

import app.manyak.notice.entity.NoticeDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 게시일·수정일 표시. 서버 시각은 UTC 라 기기 시간대로 자르면 자정 전후가 전날로 보이므로 웹과 같이 KST 로 고정한다.
 * `SimpleDateFormat` 은 스레드 안전하지 않아 호출마다 만든다 — 목록 한 페이지에 20개뿐이다.
 */
fun formatNoticeDate(epochMillis: Long): String =
    SimpleDateFormat(DATE_PATTERN, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone(DISPLAY_TIME_ZONE) }
        .format(Date(epochMillis))

/** 게시한 뒤 날짜가 바뀔 만큼 고쳐 썼을 때만 수정일을 보인다. 같은 날의 오타 수정은 알릴 일이 아니다. */
fun NoticeDetail.updatedDateOrNull(): String? {
    val published = publishedAtMillis ?: return null
    val updated = updatedAtMillis ?: return null
    if (updated <= published) return null
    return formatNoticeDate(updated).takeIf { it != formatNoticeDate(published) }
}

private const val DATE_PATTERN = "yyyy-MM-dd"
private const val DISPLAY_TIME_ZONE = "Asia/Seoul"
