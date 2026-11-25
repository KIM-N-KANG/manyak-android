package app.manyak.notification.inbox.presentation

import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.DeepLink
import app.manyak.core.navigation.NoticeDetailRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.notification.inbox.entity.InboxNotification

internal fun InboxNotification.destination(): NavKey? =
    deepLink?.let(DeepLink::routeOf)
        ?: noticeId?.takeIf(String::isNotBlank)?.let(::NoticeDetailRoute)
        ?: storyId?.takeIf(String::isNotBlank)?.let(::StoryDetailRoute)
