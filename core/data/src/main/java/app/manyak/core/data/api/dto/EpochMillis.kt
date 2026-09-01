package app.manyak.core.data.api.dto

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * ISO 8601 시각을 epoch millis 로 바꾼다. 상대 시간을 세는 일도 표시용 날짜로 옮기는 일도 날짜
 * 문자열이 아니라 시점 값을 필요로 하므로, DTO 계층의 시각 해석을 이 함수 하나로 모은다.
 *
 * `java.time` 을 쓰려고 코어 라이브러리 디슈가링을 켜지 않는다(minSdk 24). 서버 계약은 UTC 지만
 * 오프셋이 붙어 와도 맞게 읽는다. 오프셋을 무시하면 시간대가 바뀐 날 방금 진행한 채팅이
 * "9시간 전"으로 보이는 식으로 조용히 어긋난다.
 *
 * 형식이 예상과 다르면 null 을 돌려준다 — 화면이 그 자리를 그리지 않는다.
 */
internal fun String.toEpochMillisOrNull(): Long? {
    val match = InstantPattern.matchEntire(trim()) ?: return null
    val (dateTime, fraction, zone) = match.destructured
    // 정규식이 자릿수를 이미 강제하므로 숫자 변환은 실패하지 않는다.
    val fields = dateTime.split('-', 'T', 't', ' ', ':').map(String::toInt)
    val (year, month, day) = fields
    val (hour, minute, second) = fields.drop(DATE_FIELD_COUNT)

    // 관대 모드에서는 13월·32일이 조용히 다음 달로 넘어가, 잘못된 값이 그럴듯한 시각이 된다.
    val calendar = GregorianCalendar(UtcTimeZone).apply { isLenient = false }
    calendar.clear()
    return runCatching {
        calendar.set(year, month - 1, day, hour, minute, second)
        // 소수점 이하 자릿수는 서버 구성에 따라 달라지므로 밀리초 세 자리로 맞춘다.
        calendar.set(Calendar.MILLISECOND, fraction.padEnd(MILLIS_DIGITS, '0').take(MILLIS_DIGITS).toInt())
        calendar.timeInMillis - zone.toZoneOffsetMillis()
    }.getOrNull()
}

/** 빈 문자열은 오프셋 표기가 없는 경우이고, 서버 계약대로 UTC 로 읽는다. */
private fun String.toZoneOffsetMillis(): Long {
    if (isEmpty() || equals("Z", ignoreCase = true)) return 0
    val digits = drop(1).replace(":", "")
    val hours = digits.take(2).toLong()
    val minutes = digits.drop(2).ifEmpty { "0" }.toLong()
    val magnitude = hours * MILLIS_PER_HOUR + minutes * MILLIS_PER_MINUTE
    return if (first() == '-') -magnitude else magnitude
}

private val InstantPattern =
    Regex("""^(\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|z|[+-]\d{2}:?\d{2})?$""")

private val UtcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

/** 쪼갠 여섯 칸 중 앞의 셋이 날짜, 뒤의 셋이 시각이다. */
private const val DATE_FIELD_COUNT = 3

private const val MILLIS_DIGITS = 3
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
