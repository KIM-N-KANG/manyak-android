package app.manyak.notice.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notice.domain.NoticeReadRepository
import app.manyak.notice.domain.NoticeRepository
import app.manyak.notice.entity.NoticePage
import app.manyak.notice.entity.NoticeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoticeListUiState(
    val isLoading: Boolean = true,
    val notices: List<NoticeSummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** 목록을 그린 채로 다시 읽는 중. 골격이 아니라 당김 표시자가 이 상태를 말한다. */
    val isRefreshing: Boolean = false,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** 상세를 열어 읽은 공지. 상세에서 돌아오면 그 줄의 점만 꺼지도록 저장소를 관찰한다. */
    val readIds: List<String> = emptyList(),
) {
    val hasMore: Boolean get() = nextCursor != null
}

sealed interface NoticeListIntent {
    data object Retry : NoticeListIntent

    /** 목록을 당겨서 새로고침. 실패해도 보고 있던 목록을 지우지 않고 토스트로만 알린다. */
    data object Refresh : NoticeListIntent

    /** 목록 끝에 닿았거나 목록 끝의 다시 시도를 눌렀다. */
    data object LoadMore : NoticeListIntent
}

sealed interface NoticeListEvent {
    data object LoadStarted : NoticeListEvent

    data class Loaded(
        val page: NoticePage,
    ) : NoticeListEvent

    data object LoadFailed : NoticeListEvent

    data object RefreshStarted : NoticeListEvent

    data object RefreshFailed : NoticeListEvent

    data object LoadMoreStarted : NoticeListEvent

    data class MoreLoaded(
        val page: NoticePage,
    ) : NoticeListEvent

    data object LoadMoreFailed : NoticeListEvent

    data class ReadIdsChanged(
        val readIds: List<String>,
    ) : NoticeListEvent
}

sealed interface NoticeListEffect {
    data object ShowRefreshFailed : NoticeListEffect
}

/** 공지 목록. 게시 최신순 커서 페이지를 이어 읽는다. 목록을 보는 것은 읽음이 아니라 여기서는 읽음을 기록하지 않는다. */
@HiltViewModel
class NoticeListViewModel
    @Inject
    constructor(
        private val noticeRepository: NoticeRepository,
        readRepository: NoticeReadRepository,
    ) : MviViewModel<NoticeListIntent, NoticeListUiState, NoticeListEvent, NoticeListEffect>(NoticeListUiState()) {
        private var pageJob: Job? = null

        init {
            viewModelScope.launch {
                readRepository.readIds.collect { dispatchEvent(NoticeListEvent.ReadIdsChanged(it)) }
            }
            loadFirstPage()
        }

        override suspend fun handleIntent(intent: NoticeListIntent) {
            when (intent) {
                NoticeListIntent.Retry -> if (pageJob?.isActive != true) loadFirstPage()
                NoticeListIntent.Refresh -> loadFirstPage(refresh = true)
                NoticeListIntent.LoadMore -> loadNextPage()
            }
        }

        private fun loadFirstPage(refresh: Boolean = false) {
            pageJob?.cancel()
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(if (refresh) NoticeListEvent.RefreshStarted else NoticeListEvent.LoadStarted)
                    when (val result = noticeRepository.notices()) {
                        is DomainResult.Success -> dispatchEvent(NoticeListEvent.Loaded(result.value))
                        is DomainResult.Failure ->
                            if (refresh) {
                                dispatchEvent(NoticeListEvent.RefreshFailed)
                                dispatchEffect(NoticeListEffect.ShowRefreshFailed)
                            } else {
                                dispatchEvent(NoticeListEvent.LoadFailed)
                            }
                    }
                }
        }

        /** 목록 끝은 여러 프레임 동안 참이라 진행 중인 조회가 있으면 무시한다. 실패 뒤 자동 재요청을 멈추는 것은 화면이 맡는다. */
        private fun loadNextPage() {
            val cursor = uiState.value.nextCursor ?: return
            if (pageJob?.isActive == true) return
            pageJob =
                viewModelScope.launch {
                    dispatchEvent(NoticeListEvent.LoadMoreStarted)
                    when (val result = noticeRepository.notices(cursor)) {
                        is DomainResult.Success -> dispatchEvent(NoticeListEvent.MoreLoaded(result.value))
                        is DomainResult.Failure -> dispatchEvent(NoticeListEvent.LoadMoreFailed)
                    }
                }
        }

        override fun reduce(
            state: NoticeListUiState,
            event: NoticeListEvent,
        ): NoticeListUiState =
            when (event) {
                NoticeListEvent.LoadStarted ->
                    state.copy(
                        isLoading = true,
                        loadFailed = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        loadMoreFailed = false,
                    )

                // 진행 중이던 다음 페이지는 취소됐다.
                NoticeListEvent.RefreshStarted ->
                    state.copy(isRefreshing = true, isLoadingMore = false, loadMoreFailed = false)

                // 새로고침 실패는 보고 있던 목록을 건드리지 않는다 — 알림은 토스트가 맡는다.
                NoticeListEvent.RefreshFailed -> state.copy(isRefreshing = false)

                is NoticeListEvent.Loaded ->
                    state.copy(
                        isLoading = false,
                        notices = event.page.items.distinctBy { it.id },
                        nextCursor = event.page.nextCursor,
                        loadFailed = false,
                        isRefreshing = false,
                    )

                NoticeListEvent.LoadFailed ->
                    state.copy(
                        isLoading = false,
                        notices = emptyList(),
                        nextCursor = null,
                        loadFailed = true,
                        isRefreshing = false,
                    )

                NoticeListEvent.LoadMoreStarted -> state.copy(isLoadingMore = true, loadMoreFailed = false)

                is NoticeListEvent.MoreLoaded ->
                    state.copy(
                        notices = (state.notices + event.page.items).distinctBy { it.id },
                        nextCursor = event.page.nextCursor,
                        isLoadingMore = false,
                    )

                // 이미 그린 목록은 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
                NoticeListEvent.LoadMoreFailed -> state.copy(isLoadingMore = false, loadMoreFailed = true)

                is NoticeListEvent.ReadIdsChanged -> state.copy(readIds = event.readIds)
            }
    }
