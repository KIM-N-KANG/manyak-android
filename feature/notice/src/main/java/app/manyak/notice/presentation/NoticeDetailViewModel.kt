package app.manyak.notice.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notice.domain.NoticeReadRepository
import app.manyak.notice.domain.NoticeRepository
import app.manyak.notice.entity.NoticeDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class NoticeDetailUiState(
    val isLoading: Boolean = true,
    val notice: NoticeDetail? = null,
    val loadFailed: Boolean = false,
    /** 미게시·삭제(404). 다시 시도해도 낫지 않아 목록으로 돌아가는 길만 둔다. */
    val notFound: Boolean = false,
)

sealed interface NoticeDetailIntent {
    data object Retry : NoticeDetailIntent
}

sealed interface NoticeDetailEvent {
    data object LoadStarted : NoticeDetailEvent

    data class Loaded(
        val notice: NoticeDetail,
    ) : NoticeDetailEvent

    data object NotFound : NoticeDetailEvent

    data object LoadFailed : NoticeDetailEvent
}

/**
 * 공지 상세. 공지를 받으면 읽은 것으로 기록한다 — 목록에서 왔든 푸시로 바로 왔든 같다.
 * 404 는 읽음으로 기록하지 않는다. 없는 공지가 읽음 목록 자리를 차지할 이유가 없다.
 */
@HiltViewModel(assistedFactory = NoticeDetailViewModel.Factory::class)
class NoticeDetailViewModel
    @AssistedInject
    constructor(
        @Assisted private val noticeId: String,
        private val noticeRepository: NoticeRepository,
        private val readRepository: NoticeReadRepository,
    ) : MviViewModel<NoticeDetailIntent, NoticeDetailUiState, NoticeDetailEvent, Nothing>(NoticeDetailUiState()) {
        @AssistedFactory
        interface Factory {
            fun create(noticeId: String): NoticeDetailViewModel
        }

        private var loadJob: Job? = null

        init {
            load()
        }

        override suspend fun handleIntent(intent: NoticeDetailIntent) {
            when (intent) {
                NoticeDetailIntent.Retry -> if (loadJob?.isActive != true) load()
            }
        }

        private fun load() {
            loadJob =
                viewModelScope.launch {
                    dispatchEvent(NoticeDetailEvent.LoadStarted)
                    when (val result = noticeRepository.notice(noticeId)) {
                        is DomainResult.Success -> {
                            dispatchEvent(NoticeDetailEvent.Loaded(result.value))
                            readRepository.markRead(result.value.id)
                        }
                        is DomainResult.Failure -> dispatchEvent(result.error.toEvent())
                    }
                }
        }

        private fun DomainError.toEvent(): NoticeDetailEvent =
            if (this is DomainError.Server && status == HTTP_NOT_FOUND) {
                NoticeDetailEvent.NotFound
            } else {
                NoticeDetailEvent.LoadFailed
            }

        override fun reduce(
            state: NoticeDetailUiState,
            event: NoticeDetailEvent,
        ): NoticeDetailUiState =
            when (event) {
                NoticeDetailEvent.LoadStarted -> state.copy(isLoading = true, loadFailed = false, notFound = false)
                is NoticeDetailEvent.Loaded -> state.copy(isLoading = false, notice = event.notice)
                NoticeDetailEvent.NotFound -> state.copy(isLoading = false, notice = null, notFound = true)
                NoticeDetailEvent.LoadFailed -> state.copy(isLoading = false, loadFailed = true)
            }

        private companion object {
            const val HTTP_NOT_FOUND = 404
        }
    }
