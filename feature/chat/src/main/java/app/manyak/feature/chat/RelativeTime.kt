package app.manyak.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.core.ui.R
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * 카드가 그리는 마지막 활동 시각의 갈래.
 *
 * 경계는 웹 `formatRelativeDate` 와 같다 — 플랫폼 관례를 따라 다른 계단을 쓰면 같은 채팅의
 * 마지막 활동이 두 클라이언트에서 다른 시각으로 읽힌다.
 */
internal sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Minutes(
        val value: Int,
    ) : RelativeTime

    data class Hours(
        val value: Int,
    ) : RelativeTime

    data object Today : RelativeTime

    data object Yesterday : RelativeTime

    data class Days(
        val value: Int,
    ) : RelativeTime

    data class AbsoluteDate(
        val text: String,
    ) : RelativeTime
}

/**
 * [epochMillis] 를 [nowMillis] 기준의 상대 시간으로 바꾼다. "지금"을 안에서 읽지 않고 인자로 받는
 * 순수 함수라, 여섯 경계를 단위 테스트로 고정할 수 있다.
 *
 * 앞의 세 갈래는 지난 시간으로, 뒤의 세 갈래는 **기기 시간대의 달력 날짜**로 센다. 12시간이 지나야
 * "오늘"·"어제"에 닿으므로, 오늘 오전에 진행한 채팅을 오후에 보면 "오늘"이 아니라 "n시간 전"이다.
 */
internal fun relativeTimeOf(
    epochMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): RelativeTime {
    val elapsedMillis = nowMillis - epochMillis
    // 기기 시계가 서버보다 뒤처져 미래 시각이 와도 "방금 전"으로 읽는다 — 웹과 같은 처리다.
    if (elapsedMillis < MILLIS_PER_MINUTE) return RelativeTime.JustNow
    if (elapsedMillis < MILLIS_PER_HOUR) return RelativeTime.Minutes((elapsedMillis / MILLIS_PER_MINUTE).toInt())
    if (elapsedMillis < RECENT_HOUR_LIMIT * MILLIS_PER_HOUR) {
        return RelativeTime.Hours((elapsedMillis / MILLIS_PER_HOUR).toInt())
    }
    return when (val days = calendarDaysBetween(epochMillis, nowMillis, timeZone)) {
        0 -> RelativeTime.Today
        1 -> RelativeTime.Yesterday
        in 2 until DAY_LIMIT -> RelativeTime.Days(days)
        else -> RelativeTime.AbsoluteDate(formatLocalDate(epochMillis, timeZone))
    }
}

@Composable
internal fun RelativeTime.label(): String =
    when (this) {
        RelativeTime.JustNow -> stringResource(R.string.relative_time_just_now)
        is RelativeTime.Minutes -> stringResource(R.string.relative_time_minutes, value)
        is RelativeTime.Hours -> stringResource(R.string.relative_time_hours, value)
        RelativeTime.Today -> stringResource(R.string.relative_time_today)
        RelativeTime.Yesterday -> stringResource(R.string.relative_time_yesterday)
        is RelativeTime.Days -> stringResource(R.string.relative_time_days, value)
        is RelativeTime.AbsoluteDate -> text
    }

/** 지난 시간이 아니라 자정 경계를 몇 번 넘었는지를 센다 — 23시 55분과 0시 5분은 하루 차이다. */
private fun calendarDaysBetween(
    fromMillis: Long,
    toMillis: Long,
    timeZone: TimeZone,
): Int {
    val elapsedBetweenMidnights = startOfDayMillis(toMillis, timeZone) - startOfDayMillis(fromMillis, timeZone)
    // 서머타임이 있는 시간대에서는 하루가 23·25시간이라 나눗셈이 딱 떨어지지 않는다.
    return (elapsedBetweenMidnights.toDouble() / MILLIS_PER_DAY).roundToInt()
}

private fun startOfDayMillis(
    epochMillis: Long,
    timeZone: TimeZone,
): Long =
    GregorianCalendar(timeZone)
        .apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

/**
 * 기기 시간대의 `YYYY-MM-DD`. 스토리 상세의 생성일이 서버 문자열의 UTC 날짜를 그대로 자르는 것과
 * 갈리는데, 이 자리는 웹과 같은 날짜를 보여야 하고 웹은 브라우저 시간대로 그린다.
 */
private fun formatLocalDate(
    epochMillis: Long,
    timeZone: TimeZone,
): String {
    val calendar = GregorianCalendar(timeZone).apply { timeInMillis = epochMillis }
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L

/** 이 시간을 넘기면 지난 시간 대신 달력 날짜로 말한다. */
private const val RECENT_HOUR_LIMIT = 12

/** 이 날수부터는 상대 표현 대신 날짜를 그대로 쓴다. */
private const val DAY_LIMIT = 7
