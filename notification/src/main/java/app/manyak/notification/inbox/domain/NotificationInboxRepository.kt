package app.manyak.notification.inbox.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.notification.inbox.entity.NotificationCategory
import app.manyak.notification.inbox.entity.NotificationPage
import app.manyak.notification.inbox.entity.NotificationUnreadCount

interface NotificationInboxRepository {
    suspend fun list(
        category: NotificationCategory,
        cursor: String? = null,
    ): DomainResult<NotificationPage>

    suspend fun unreadCount(): DomainResult<NotificationUnreadCount>

    suspend fun read(id: String): DomainResult<Unit>

    suspend fun readAll(): DomainResult<Unit>
}
