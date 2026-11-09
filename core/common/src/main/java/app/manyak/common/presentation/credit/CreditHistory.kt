package app.manyak.common.presentation.credit

import app.manyak.common.domain.credit.CreditTransactionReader
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.credit.CreditTransaction
import app.manyak.common.entity.credit.CreditTransactionPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 이프 내역 목록이 그리는 상태. 화면 상태에 이 덩어리째 들어간다. */
data class CreditHistoryUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val items: List<CreditTransaction> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** 보던 목록 위에서 첫 페이지를 다시 읽는 중. 응답까지 목록은 그대로 둔다. */
    val isRefreshing: Boolean = false,
) {
    /**
     * 목록 끝에 닿으면 다음 페이지를 이어 읽어도 되는지. 실패한 뒤에는 끝에 머무는 동안 같은 실패를
     * 반복하게 되므로 자동으로 다시 읽지 않고 재시도 버튼이 이어받는다.
     */
    val canLoadMore: Boolean
        get() = nextCursor != null && !isLoadingMore && !loadMoreFailed && !isRefreshing && items.isNotEmpty()
}

/** 상태 전이의 입력. 화면의 Event 가 이것을 감싸 나른다. */
sealed interface CreditHistoryChange {
    data object LoadStarted : CreditHistoryChange

    data class Loaded(
        val page: CreditTransactionPage,
    ) : CreditHistoryChange

    data object LoadFailed : CreditHistoryChange

    data object LoadMoreStarted : CreditHistoryChange

    data class MoreLoaded(
        val page: CreditTransactionPage,
    ) : CreditHistoryChange

    data object LoadMoreFailed : CreditHistoryChange

    data object RefreshStarted : CreditHistoryChange

    data object RefreshFailed : CreditHistoryChange
}

fun CreditHistoryUiState.reduceCreditHistory(change: CreditHistoryChange): CreditHistoryUiState =
    when (change) {
        CreditHistoryChange.LoadStarted -> copy(isLoading = true, loadFailed = false)
        is CreditHistoryChange.Loaded ->
            CreditHistoryUiState(isLoading = false, items = change.page.items, nextCursor = change.page.nextCursor)

        CreditHistoryChange.LoadFailed -> copy(isLoading = false, loadFailed = true)
        CreditHistoryChange.LoadMoreStarted -> copy(isLoadingMore = true, loadMoreFailed = false)
        is CreditHistoryChange.MoreLoaded ->
            copy(isLoadingMore = false, items = items + change.page.items, nextCursor = change.page.nextCursor)
        // 이미 그린 목록은 지우지 않는다. 실패한 것은 다음 페이지뿐이다.
        CreditHistoryChange.LoadMoreFailed -> copy(isLoadingMore = false, loadMoreFailed = true)
        // 읽던 다음 페이지는 새 첫 페이지와 이어지지 않아 버린다. 성공하면 Loaded 가 첫 페이지 하나로 바꾼다.
        CreditHistoryChange.RefreshStarted -> copy(isRefreshing = true, isLoadingMore = false)
        // 보던 목록은 그대로 둔다. 실패한 것은 다시 읽기뿐이다.
        CreditHistoryChange.RefreshFailed -> copy(isRefreshing = false)
    }

/**
 * 이프 내역의 조회 절차. 이프 충전의 내역 탭과 채팅 메뉴의 이프 내역이 같은 목록을 쓰므로 여기 둔다.
 *
 * 상태를 직접 들지 않고 [emit] 으로 전이만 올려보낸다. 화면 상태의 정본은 각 ViewModel 이다.
 * 조회는 [scope] 의 자식 작업으로 돌려 화면의 다른 입력을 막지 않는다.
 */
class CreditHistoryController(
    private val scope: CoroutineScope,
    private val reader: CreditTransactionReader,
    private val emit: suspend (CreditHistoryChange) -> Unit,
) {
    private var job: Job? = null

    /** 첫 페이지를 읽는다. 이미 읽는 중이면 그 결과를 기다린다. */
    fun load() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                emit(CreditHistoryChange.LoadStarted)
                when (val result = reader.getTransactions()) {
                    is DomainResult.Success -> emit(CreditHistoryChange.Loaded(result.value))
                    is DomainResult.Failure -> emit(CreditHistoryChange.LoadFailed)
                }
            }
    }

    /** 다음 페이지. 커서는 서버가 준 값을 그대로 되돌려 주기만 하고, 읽는 중이면 겹쳐 요청하지 않는다. */
    fun loadMore(state: CreditHistoryUiState) {
        val cursor = state.nextCursor ?: return
        if (state.isLoading || job?.isActive == true) return
        job =
            scope.launch {
                emit(CreditHistoryChange.LoadMoreStarted)
                when (val result = reader.getTransactions(cursor)) {
                    is DomainResult.Success -> emit(CreditHistoryChange.MoreLoaded(result.value))
                    is DomainResult.Failure -> emit(CreditHistoryChange.LoadMoreFailed)
                }
            }
    }

    /**
     * 받아 둔 뒷 페이지를 버리고 커서 없이 첫 페이지만 다시 읽는다. 첫 조회 중이거나 이미 다시 읽는 중이면
     * 그 결과를 기다린다. 읽던 다음 페이지는 취소한다. 새 첫 페이지 뒤에 이어 붙으면 순서가 어긋난다.
     */
    fun refresh(state: CreditHistoryUiState) {
        if (state.isLoading || state.isRefreshing) return
        job?.cancel()
        job =
            scope.launch {
                emit(CreditHistoryChange.RefreshStarted)
                when (val result = reader.getTransactions()) {
                    is DomainResult.Success -> emit(CreditHistoryChange.Loaded(result.value))
                    is DomainResult.Failure -> emit(CreditHistoryChange.RefreshFailed)
                }
            }
    }

    /** 읽던 페이지를 버린다. 내역을 떠날 때 불러 닫은 뒤 늦게 온 응답이 상태를 되살리지 않게 한다. */
    fun cancel() {
        job?.cancel()
    }
}
