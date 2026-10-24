package app.manyak.notification.inbox.entity

enum class NotificationCategory { NEWS, ACTIVITY }

data class InboxNotification(
    val id: String,
    val category: NotificationCategory,
    val type: String,
    val title: String,
    val body: String = "",
    val deepLink: String? = null,
    val noticeId: String? = null,
    val storyId: String? = null,
    val noticeTag: String? = null,
    val createdAtMillis: Long? = null,
    val isRead: Boolean = false,
)

data class NotificationPage(
    val items: List<InboxNotification>,
    val nextCursor: String?,
)

data class NotificationUnreadCount(
    val total: Long = 0,
    val news: Long = 0,
    val activity: Long = 0,
) {
    fun forCategory(category: NotificationCategory): Long =
        if (category == NotificationCategory.NEWS) news else activity

    fun readOne(category: NotificationCategory): NotificationUnreadCount =
        copy(
            total = (total - 1).coerceAtLeast(0),
            news = (news - if (category == NotificationCategory.NEWS) 1 else 0).coerceAtLeast(0),
            activity = (activity - if (category == NotificationCategory.ACTIVITY) 1 else 0).coerceAtLeast(0),
        )
}
