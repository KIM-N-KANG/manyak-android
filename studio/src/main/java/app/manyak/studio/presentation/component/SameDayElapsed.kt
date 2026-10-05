package app.manyak.studio.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import app.manyak.studio.R as StudioR

/** 오늘 만든 스토리의 지난 시간. 웹 제작 탭 카드와 같이 어제부터는 날짜를 그대로 쓴다. */
internal sealed interface SameDayElapsed {
    data object JustNow : SameDayElapsed

    data class Minutes(
        val value: Int,
    ) : SameDayElapsed

    data class Hours(
        val value: Int,
    ) : SameDayElapsed
}

/** [epochMillis]가 [nowMillis]와 기기 시간대로 같은 날이 아니면 null 이다. */
internal fun sameDayElapsedOf(
    epochMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): SameDayElapsed? {
    if (!sameDay(epochMillis, nowMillis, timeZone)) return null
    val elapsed = nowMillis - epochMillis
    return when {
        // 기기 시계가 서버보다 뒤처져 미래 시각이 와도 "방금 전"으로 읽는다.
        elapsed < MILLIS_PER_MINUTE -> SameDayElapsed.JustNow
        elapsed < MILLIS_PER_HOUR -> SameDayElapsed.Minutes((elapsed / MILLIS_PER_MINUTE).toInt())
        else -> SameDayElapsed.Hours((elapsed / MILLIS_PER_HOUR).toInt())
    }
}

@Composable
internal fun SameDayElapsed.label(): String =
    when (this) {
        SameDayElapsed.JustNow -> stringResource(StudioR.string.studio_story_created_just_now)
        is SameDayElapsed.Minutes -> stringResource(StudioR.string.studio_story_created_minutes, value)
        is SameDayElapsed.Hours -> stringResource(StudioR.string.studio_story_created_hours, value)
    }

private fun sameDay(
    first: Long,
    second: Long,
    timeZone: TimeZone,
): Boolean {
    val a = GregorianCalendar(timeZone).apply { timeInMillis = first }
    val b = GregorianCalendar(timeZone).apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
