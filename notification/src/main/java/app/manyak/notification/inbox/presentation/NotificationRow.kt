package app.manyak.notification.inbox.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notification.inbox.entity.InboxNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import app.manyak.notification.R as NotificationR

@Composable
internal fun NotificationRow(
    item: InboxNotification,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.labelRes()?.let {
                Text(
                    stringResource(it),
                    style = ManyakTheme.typography.labelSmall,
                    color = ManyakTheme.colors.textSubtlest,
                )
            }
            Spacer(Modifier.weight(1f))
            item.createdAtMillis?.let {
                Text(
                    notificationTime(it),
                    style = ManyakTheme.typography.labelSmall,
                    color = ManyakTheme.colors.textSubtlest,
                )
            }
        }
        NotificationTitle(item)
        if (item.body.isNotBlank()) {
            Text(
                item.body,
                style = ManyakTheme.typography.bodyMedium,
                color = if (item.isRead) ManyakTheme.colors.textSubtlest else ManyakTheme.colors.textSubtle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NotificationTitle(item: InboxNotification) {
    val unread = stringResource(NotificationR.string.inbox_unread)
    val dotSize = with(LocalDensity.current) { ManyakTheme.sizes.unreadDotInline.toSp() }
    val titleStyle = if (item.isRead) ManyakTheme.typography.bodyLarge else ManyakTheme.typography.bodyLargeStrong
    // 점을 제목 글에 붙이면 말줄임에 함께 잘리므로, 제목만 줄이고 점은 따로 둬 줄 끝에 남긴다.
    Row {
        Text(
            item.title,
            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            style = titleStyle,
            color = if (item.isRead) ManyakTheme.colors.textSubtle else ManyakTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item.isRead) {
            Text(
                text =
                    buildAnnotatedString {
                        append(' ')
                        appendInlineContent("unread", unread)
                    },
                modifier = Modifier.alignByBaseline(),
                inlineContent =
                    mapOf(
                        "unread" to
                            InlineTextContent(
                                Placeholder(dotSize, dotSize, PlaceholderVerticalAlign.TextTop),
                            ) { InboxUnreadDot(unread) },
                    ),
                style = titleStyle,
            )
        }
    }
}

@Composable
internal fun InboxUnreadDot(description: String) {
    Box(
        Modifier
            .size(
                ManyakTheme.sizes.unreadDotInline,
            ).background(ManyakTheme.colors.backgroundDangerBold, ManyakTheme.shapes.pill)
            .semantics {
                contentDescription =
                    description
            },
    )
}

private val noticeTagLabels =
    mapOf(
        "SERVICE" to NotificationR.string.inbox_tag_service,
        "UPDATE" to NotificationR.string.inbox_tag_update,
        "MAINTENANCE" to NotificationR.string.inbox_tag_maintenance,
        "EVENT" to NotificationR.string.inbox_tag_event,
        "PROMOTION" to NotificationR.string.inbox_tag_promotion,
    )

private val typeLabels =
    mapOf(
        "NOTICE" to NotificationR.string.inbox_notice,
        "PROMOTION" to NotificationR.string.inbox_promotion,
        "STORY_COMPLETED" to NotificationR.string.inbox_story_completed,
        "STORY_MODERATION_COMPLETED" to NotificationR.string.inbox_review_completed,
        "ATTENDANCE_REMINDER" to NotificationR.string.inbox_attendance,
        "CREDIT_EXPIRY_REMINDER" to NotificationR.string.inbox_credit_expiry,
        "STORY_TAKEN_DOWN" to NotificationR.string.inbox_story_taken_down,
    )

internal fun InboxNotification.labelRes(): Int? = noticeTagLabels[noticeTag] ?: typeLabels[type]

internal data class NotificationTime(
    val label: Int? = null,
    val amount: Long = 0,
    val date: String = "",
)

internal fun notificationTimeValue(
    at: Long,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
): NotificationTime {
    val minutes = ((now - at) / MILLIS_PER_MINUTE).coerceAtLeast(0)
    val dayMillis = TimeUnit.DAYS.toMillis(1)
    val days =
        Math.floorDiv(now + zone.getOffset(now), dayMillis) -
            Math.floorDiv(at + zone.getOffset(at), dayMillis)
    return when {
        minutes < 1 -> NotificationTime(NotificationR.string.inbox_just_now)
        minutes < MINUTES_PER_HOUR -> NotificationTime(NotificationR.string.inbox_minutes_ago, minutes)
        minutes < RELATIVE_HOUR_LIMIT * MINUTES_PER_HOUR ->
            NotificationTime(
                NotificationR.string.inbox_hours_ago,
                minutes / MINUTES_PER_HOUR,
            )
        days == 0L -> NotificationTime(NotificationR.string.inbox_today)
        days == 1L -> NotificationTime(NotificationR.string.inbox_yesterday)
        days < RELATIVE_DAY_LIMIT -> NotificationTime(NotificationR.string.inbox_days_ago, days)
        else ->
            NotificationTime(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(at)),
            )
    }
}

@Composable
private fun notificationTime(at: Long): String {
    val value = notificationTimeValue(at, System.currentTimeMillis())
    return value.label?.let { stringResource(it, value.amount) } ?: value.date
}

private const val MILLIS_PER_MINUTE = 60_000
private const val MINUTES_PER_HOUR = 60
private const val RELATIVE_HOUR_LIMIT = 12
private const val RELATIVE_DAY_LIMIT = 7
