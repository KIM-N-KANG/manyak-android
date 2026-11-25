package app.manyak.notification.inbox.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notification.inbox.domain.NotificationInboxRepository
import app.manyak.notification.inbox.entity.InboxNotification
import app.manyak.notification.inbox.entity.NotificationCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class NotificationInboxViewModel
    @Inject
    constructor(
        private val repository: NotificationInboxRepository,
    ) : MviViewModel<
            NotificationInboxIntent,
            NotificationInboxUiState,
            NotificationInboxEvent,
            NotificationInboxEffect,
        >(
            NotificationInboxUiState(),
        ) {
        // 읽기 전의 목록 응답이 낙관적으로 바꾼 읽음 상태를 되살리지 않게 한다.
        private val requests = Mutex()
        private val pageJobs = mutableMapOf<NotificationCategory, Job>()
        private val readingIds = mutableSetOf<String>()
        private var readAllJob: Job? = null
        private var selected = NotificationCategory.NEWS

        init {
            load(selected)
        }

        override suspend fun handleIntent(intent: NotificationInboxIntent) {
            when (intent) {
                is NotificationInboxIntent.Select -> {
                    selected = intent.category
                    dispatchEvent(NotificationInboxEvent.Selected(selected))
                    if (uiState.value.pages[selected]?.loaded != true) load(selected)
                }
                NotificationInboxIntent.Refresh -> load(selected)
                NotificationInboxIntent.LoadMore -> {
                    val cursor = uiState.value.pages[selected]?.nextCursor ?: return
                    load(selected, cursor)
                }
                is NotificationInboxIntent.Open -> open(intent.item)
                NotificationInboxIntent.ReadAll -> readAll()
            }
        }

        private fun load(
            category: NotificationCategory,
            cursor: String? = null,
        ) {
            if (pageJobs[category]?.isActive == true) return
            pageJobs[category] =
                viewModelScope.launch {
                    requests.withLock {
                        dispatchEvent(NotificationInboxEvent.Loading(category, cursor != null))
                        val result = repository.list(category, cursor)
                        if (cursor == null) refreshCount()
                        when (result) {
                            is DomainResult.Success ->
                                dispatchEvent(
                                    NotificationInboxEvent.Loaded(
                                        category,
                                        result.value,
                                        cursor != null,
                                    ),
                                )
                            is DomainResult.Failure -> {
                                dispatchEvent(NotificationInboxEvent.Failed(category, result.error, cursor != null))
                                if (cursor == null && uiState.value.pages[category]?.loaded == true) {
                                    dispatchEffect(NotificationInboxEffect.RefreshFailed(result.error))
                                }
                            }
                        }
                    }
                }
        }

        private suspend fun open(item: InboxNotification) {
            if (!readingIds.add(item.id)) return
            viewModelScope.launch {
                try {
                    requests.withLock {
                        val current =
                            uiState.value.pages[item.category]
                                ?.items
                                ?.find { it.id == item.id } ?: item
                        if (!current.isRead) {
                            dispatchEvent(NotificationInboxEvent.Read(current))
                            repository.read(item.id)
                            refreshCount()
                        }
                    }
                    item.destination()?.let { dispatchEffect(NotificationInboxEffect.Navigate(it)) }
                } finally {
                    readingIds.remove(item.id)
                }
            }
        }

        private fun readAll() {
            if (readAllJob?.isActive == true || uiState.value.unread.total == 0L) return
            readAllJob =
                viewModelScope.launch {
                    requests.withLock {
                        val snapshot = uiState.value
                        dispatchEvent(NotificationInboxEvent.ReadingAll)
                        when (repository.readAll()) {
                            is DomainResult.Success -> dispatchEvent(NotificationInboxEvent.ReadAllFinished())
                            is DomainResult.Failure -> {
                                dispatchEvent(NotificationInboxEvent.ReadAllFinished(snapshot))
                                dispatchEffect(NotificationInboxEffect.ReadAllFailed)
                            }
                        }
                        refreshCount()
                    }
                }
        }

        private suspend fun refreshCount() {
            val count =
                when (val result = repository.unreadCount()) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> return
                }
            dispatchEvent(NotificationInboxEvent.Counted(count))
        }

        override fun reduce(
            state: NotificationInboxUiState,
            event: NotificationInboxEvent,
        ): NotificationInboxUiState = reduceInbox(state, event)
    }
