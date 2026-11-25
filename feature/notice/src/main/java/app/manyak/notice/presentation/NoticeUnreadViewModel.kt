package app.manyak.notice.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notice.domain.NoticeReadRepository
import app.manyak.notice.domain.NoticeRepository
import app.manyak.notice.domain.hasUnread
import app.manyak.notice.entity.NoticeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NoticeUnreadIntent {
    /** 셸이 다시 보일 때마다 첫 페이지를 새로 읽는다. 그 사이 게시된 공지가 점으로 드러난다. */
    data object Refresh : NoticeUnreadIntent
}

data class NoticeUnreadUiState(
    val hasUnread: Boolean = false,
)

sealed interface NoticeUnreadEvent {
    data class Changed(
        val hasUnread: Boolean,
    ) : NoticeUnreadEvent
}

/**
 * 헤더 종 버튼의 점과 마이 메뉴의 배지가 공유하는 "안 읽은 공지 있음" 판정. 목록 첫 페이지(서버 기본 20건)만 본다 —
 * 그 뒤는 게시 30일을 넘겼을 가능성이 높고, 상한이 있어야 요청이 하나로 끝난다. 조회 실패는 점을 끈다.
 */
@HiltViewModel
class NoticeUnreadViewModel
    @Inject
    constructor(
        private val noticeRepository: NoticeRepository,
        private val readRepository: NoticeReadRepository,
    ) : MviViewModel<NoticeUnreadIntent, NoticeUnreadUiState, NoticeUnreadEvent, Nothing>(NoticeUnreadUiState()) {
        private var notices: List<NoticeSummary> = emptyList()
        private var readIds: List<String> = emptyList()
        private var loadJob: Job? = null

        init {
            viewModelScope.launch {
                readRepository.readIds.collect { ids ->
                    readIds = ids
                    publish()
                }
            }
            refresh()
        }

        override suspend fun handleIntent(intent: NoticeUnreadIntent) {
            when (intent) {
                NoticeUnreadIntent.Refresh -> refresh()
            }
        }

        private fun refresh() {
            if (loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    notices =
                        when (val result = noticeRepository.notices()) {
                            is DomainResult.Success -> result.value.items
                            is DomainResult.Failure -> emptyList()
                        }
                    publish()
                }
        }

        private suspend fun publish() {
            dispatchEvent(NoticeUnreadEvent.Changed(notices.hasUnread(readIds, System.currentTimeMillis())))
        }

        override fun reduce(
            state: NoticeUnreadUiState,
            event: NoticeUnreadEvent,
        ): NoticeUnreadUiState =
            when (event) {
                is NoticeUnreadEvent.Changed -> state.copy(hasUnread = event.hasUnread)
            }
    }
