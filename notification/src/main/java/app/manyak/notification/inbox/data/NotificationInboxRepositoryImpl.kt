package app.manyak.notification.inbox.data

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import app.manyak.notification.inbox.domain.NotificationInboxRepository
import app.manyak.notification.inbox.entity.InboxNotification
import app.manyak.notification.inbox.entity.NotificationCategory
import app.manyak.notification.inbox.entity.NotificationPage
import app.manyak.notification.inbox.entity.NotificationUnreadCount
import javax.inject.Inject

class NotificationInboxRepositoryImpl
    @Inject
    constructor(
        private val api: NotificationInboxApi,
    ) : NotificationInboxRepository {
        override suspend fun list(
            category: NotificationCategory,
            cursor: String?,
        ): DomainResult<NotificationPage> =
            apiCall { api.list(category.name, cursor) }.map { page ->
                NotificationPage(page.items.map { it.toEntity(category) }, page.nextCursor)
            }

        override suspend fun unreadCount(): DomainResult<NotificationUnreadCount> =
            apiCall { api.unreadCount() }.map { NotificationUnreadCount(it.total, it.news, it.activity) }

        override suspend fun read(id: String): DomainResult<Unit> = emptyBodyApiCall { api.read(id) }

        override suspend fun readAll(): DomainResult<Unit> = emptyBodyApiCall { api.readAll() }
    }

private fun NotificationItemDto.toEntity(category: NotificationCategory) =
    InboxNotification(
        id = notificationId,
        category = category,
        type = type,
        title = title,
        body = body,
        deepLink = deepLink,
        storyId = storyId,
        noticeId = noticeId,
        noticeTag = noticeTag,
        createdAtMillis = createdAt?.toEpochMillisOrNull(),
        isRead = readAt != null,
    )
