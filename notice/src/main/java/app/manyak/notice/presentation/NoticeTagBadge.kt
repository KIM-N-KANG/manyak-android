package app.manyak.notice.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.manyak.designsystem.component.StoryBadgeScale
import app.manyak.designsystem.component.StoryGenreBadge
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notice.R as NoticeR

@Composable
internal fun NoticeTagBadge(
    tag: String?,
    scale: StoryBadgeScale = StoryBadgeScale.Large,
) {
    val label = noticeTagLabel(tag) ?: return
    val warning = tag == "MAINTENANCE"
    StoryGenreBadge(
        text = stringResource(label),
        scale = scale,
        contentColor = if (warning) ManyakTheme.colors.textWarning else ManyakTheme.colors.textSubtle,
        containerColor =
            if (warning) {
                ManyakTheme.colors.backgroundWarningSubtle
            } else {
                ManyakTheme.colors.backgroundNeutral
            },
    )
}

internal fun noticeTagLabel(tag: String?): Int? =
    when (tag) {
        "SERVICE" -> NoticeR.string.notice_tag_service
        "UPDATE" -> NoticeR.string.notice_tag_update
        "MAINTENANCE" -> NoticeR.string.notice_tag_maintenance
        "EVENT" -> NoticeR.string.notice_tag_event
        "PROMOTION" -> NoticeR.string.notice_tag_promotion
        else -> null
    }
