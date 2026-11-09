package app.manyak.notification.inbox.presentation

import androidx.navigation3.runtime.NavKey
import app.manyak.common.domain.error.DomainError
import app.manyak.notification.inbox.entity.InboxNotification
import app.manyak.notification.inbox.entity.NotificationCategory
import app.manyak.notification.inbox.entity.NotificationPage
import app.manyak.notification.inbox.entity.NotificationUnreadCount

data class InboxPageState(
    val items: List<InboxNotification> = emptyList(),
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: DomainError? = null,
    val nextCursor: String? = null,
)

data class NotificationInboxUiState(
    val category: NotificationCategory = NotificationCategory.NEWS,
    val pages: Map<NotificationCategory, InboxPageState> = emptyMap(),
    val unread: NotificationUnreadCount = NotificationUnreadCount(),
    val isMarkingAllRead: Boolean = false,
) {
    val page: InboxPageState get() = pages[category] ?: InboxPageState(isLoading = true)
}

sealed interface NotificationInboxIntent {
    data class Select(
        val category: NotificationCategory,
    ) : NotificationInboxIntent

    data object Refresh : NotificationInboxIntent

    data object LoadMore : NotificationInboxIntent

    data class Open(
        val item: InboxNotification,
    ) : NotificationInboxIntent

    data object ReadAll : NotificationInboxIntent
}

sealed interface NotificationInboxEvent {
    data class Selected(
        val category: NotificationCategory,
    ) : NotificationInboxEvent

    data class Loading(
        val category: NotificationCategory,
        val append: Boolean,
    ) : NotificationInboxEvent

    data class Loaded(
        val category: NotificationCategory,
        val page: NotificationPage,
        val append: Boolean,
    ) : NotificationInboxEvent

    data class Failed(
        val category: NotificationCategory,
        val error: DomainError,
        val append: Boolean,
    ) : NotificationInboxEvent

    data class Counted(
        val unread: NotificationUnreadCount,
    ) : NotificationInboxEvent

    data class Read(
        val item: InboxNotification,
    ) : NotificationInboxEvent

    data object ReadingAll : NotificationInboxEvent

    data class ReadAllFinished(
        val rollback: NotificationInboxUiState? = null,
    ) : NotificationInboxEvent
}

sealed interface NotificationInboxEffect {
    data class Navigate(
        val destination: NavKey,
    ) : NotificationInboxEffect

    data object ReadAllFailed : NotificationInboxEffect

    data class RefreshFailed(
        val error: DomainError,
    ) : NotificationInboxEffect
}

internal fun reduceInbox(
    state: NotificationInboxUiState,
    event: NotificationInboxEvent,
): NotificationInboxUiState =
    when (event) {
        is NotificationInboxEvent.Selected -> state.copy(category = event.category)
        is NotificationInboxEvent.Loading ->
            state.updatePage(event.category) {
                copy(isLoading = true, isLoadingMore = event.append, error = null)
            }
        is NotificationInboxEvent.Loaded ->
            state.updatePage(event.category) {
                copy(
                    items = ((if (event.append) items else emptyList()) + event.page.items).distinctBy { it.id },
                    loaded = true,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                    nextCursor = event.page.nextCursor,
                )
            }
        is NotificationInboxEvent.Failed -> state.updatePage(event.category) { failed(event) }
        is NotificationInboxEvent.Counted -> state.copy(unread = event.unread)
        is NotificationInboxEvent.Read ->
            state.copy(
                pages =
                    state.pages.mapValues { (_, page) ->
                        page.copy(
                            items =
                                page.items.map {
                                    if (it.id ==
                                        event.item.id
                                    ) {
                                        it.copy(isRead = true)
                                    } else {
                                        it
                                    }
                                },
                        )
                    },
                unread = state.unread.readOne(event.item.category),
            )
        NotificationInboxEvent.ReadingAll ->
            state.copy(
                pages =
                    state.pages.mapValues { (_, page) ->
                        page.copy(items = page.items.map { it.copy(isRead = true) })
                    },
                unread = NotificationUnreadCount(),
                isMarkingAllRead = true,
            )
        is NotificationInboxEvent.ReadAllFinished ->
            state.copy(
                pages = event.rollback?.pages ?: state.pages,
                unread = event.rollback?.unread ?: state.unread,
                isMarkingAllRead = false,
            )
    }

private fun NotificationInboxUiState.updatePage(
    category: NotificationCategory,
    update: InboxPageState.() -> InboxPageState,
): NotificationInboxUiState = copy(pages = pages + (category to (pages[category] ?: InboxPageState()).update()))

private fun InboxPageState.failed(event: NotificationInboxEvent.Failed): InboxPageState =
    copy(
        isLoading = false,
        isLoadingMore = false,
        error = if (event.append || !loaded) event.error else null,
    )
