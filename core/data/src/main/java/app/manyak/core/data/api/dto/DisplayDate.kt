package app.manyak.core.data.api.dto

/**
 * ISO-8601 시각의 날짜 부분만 취한다. 화면이 필요로 하는 것이 `YYYY-MM-DD` 하나뿐이라
 * `java.time` 을 쓰려고 코어 라이브러리 디슈가링을 켜지 않는다(minSdk 24).
 *
 * 형식이 예상과 다르면 null 을 돌려준다 — 화면이 그 줄 자체를 그리지 않는다.
 */
internal fun String.toDisplayDate(): String? = take(DATE_LENGTH).takeIf { date -> date.matches(DatePattern) }

private const val DATE_LENGTH = 10

private val DatePattern = Regex("""\d{4}-\d{2}-\d{2}""")
