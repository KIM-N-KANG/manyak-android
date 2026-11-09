package app.manyak.notification.inbox.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notification.inbox.domain.NotificationInboxRepository
import app.manyak.notification.inbox.entity.NotificationUnreadCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationUnreadViewModel
    @Inject
    constructor(
        private val repository: NotificationInboxRepository,
    ) : MviViewModel<Unit, NotificationUnreadCount, NotificationUnreadCount, Nothing>(NotificationUnreadCount()) {
        private var refreshJob: Job? = null

        override suspend fun handleIntent(intent: Unit) {
            if (refreshJob?.isActive == true) return
            refreshJob =
                viewModelScope.launch {
                    val count =
                        when (val result = repository.unreadCount()) {
                            is DomainResult.Success -> result.value
                            is DomainResult.Failure -> NotificationUnreadCount()
                        }
                    dispatchEvent(count)
                }
        }

        override fun reduce(
            state: NotificationUnreadCount,
            event: NotificationUnreadCount,
        ): NotificationUnreadCount = event
    }
