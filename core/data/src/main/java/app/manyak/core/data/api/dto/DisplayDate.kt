package app.manyak.core.data.api.dto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 서버 시각을 KST 기준 `YYYY-MM-DD` 로 옮긴다. 서버가 UTC 로 내려주므로 날짜 부분만 잘라 쓰면
 * KST 자정~오전 9시에 벌어진 일이 하루 전으로 보인다.
 *
 * 기기 시간대가 아니라 KST 로 고정한다 — 출석 보상의 하루 경계와 초대 월 한도를 서버가 KST 로
 * 판정하므로, 기기 시간대를 따르면 해외에서 "적립은 됐는데 날짜가 하루 다른" 화면이 된다.
 *
 * 형식이 예상과 다르면 null 을 돌려준다 — 화면이 그 줄 자체를 그리지 않는다.
 */
internal fun String.toDisplayDate(): String? {
    // 시각 없이 날짜만 온 값은 옮길 시점이 없으므로 그대로 쓴다.
    val value = trim()
    if (value.matches(DatePattern)) return value

    val epochMillis = value.toEpochMillisOrNull() ?: return null

    // SimpleDateFormat 은 스레드 안전하지 않아 호출마다 새로 만든다.
    return SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale.US)
        .apply { timeZone = KstTimeZone }
        .format(Date(epochMillis))
}

private val DatePattern = Regex("""\d{4}-\d{2}-\d{2}""")

private const val DISPLAY_DATE_PATTERN = "yyyy-MM-dd"

private val KstTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
