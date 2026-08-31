package app.manyak.feature.my

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.domain.credit.CreditTransaction
import app.manyak.core.domain.credit.CreditTransactionReason
import app.manyak.core.domain.credit.CreditTransactionType
import app.manyak.core.ui.R
import app.manyak.core.ui.component.LoadFailedContent
import app.manyak.core.ui.component.SkeletonPlaceholder
import app.manyak.core.ui.component.rememberSkeletonPulseAlpha
import app.manyak.core.ui.theme.ManyakTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat

/**
 * 이프 내역. 잔액 상자 하나와 그 아래 원장 목록뿐이고, 분류 필터는 두지 않는다.
 *
 * 잔액 상자도 목록의 첫 항목이라 함께 스크롤된다 — 긴 목록에서 화면 위쪽을 고정으로 내주지 않는다.
 */
@Composable
fun CreditHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 이프는 채팅·제작에서 줄어들고 마이에서 출석으로 늘어난다. 화면이 보일 때마다 잔액을 맞춘다.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(CreditHistoryIntent.ScreenShown) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MyDetailHeader(titleRes = R.string.my_credit_history_title, onBack = onBack)
        CreditHistoryContent(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CreditHistoryContent(
    state: CreditHistoryUiState,
    onIntent: (CreditHistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenListEnds(listState = listState, state = state, onIntent = onIntent)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = ManyakTheme.spacing.gutter,
                end = ManyakTheme.spacing.gutter,
                bottom = ManyakTheme.spacing.screenBottom,
            ),
    ) {
        item {
            CreditBalanceBox(balance = state.balance)
            // 잔액과 내역은 다른 이야기라 다음 절 간격만큼 띄운다.
            Spacer(Modifier.height(ManyakTheme.spacing.block))
        }
        creditHistoryBody(state = state, onIntent = onIntent)
    }
}

/** 잔액·목록·상태별 자리. 상자 아래에 오는 것은 이 셋 중 하나뿐이다. */
private fun LazyListScope.creditHistoryBody(
    state: CreditHistoryUiState,
    onIntent: (CreditHistoryIntent) -> Unit,
) {
    when {
        state.isLoading -> item { CreditHistorySkeleton() }

        state.loadFailed ->
            item {
                LoadFailedContent(
                    message = stringResource(R.string.my_credit_history_load_failed),
                    onRetry = { onIntent(CreditHistoryIntent.Retry) },
                    modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.block),
                )
            }

        state.items.isEmpty() -> item { EmptyCreditHistory() }

        else -> {
            // 원장 줄에는 안정된 식별자가 없어 key 를 주지 않는다.
            items(state.items) { transaction -> CreditTransactionRow(transaction = transaction) }
            if (state.isLoadingMore || state.loadMoreFailed) {
                item {
                    CreditHistoryLoadMoreFooter(
                        isLoading = state.isLoadingMore,
                        onRetry = { onIntent(CreditHistoryIntent.LoadMore) },
                    )
                }
            }
        }
    }
}

/**
 * 목록 끝에 닿으면 다음 커서를 잇는다. **실패한 뒤에는 자동으로 다시 요청하지 않는다** —
 * 끝에 머무는 동안 같은 실패를 반복하게 되므로, 그 자리에서는 재시도 버튼이 이어받는다.
 */
@Composable
private fun LoadMoreWhenListEnds(
    listState: LazyListState,
    state: CreditHistoryUiState,
    onIntent: (CreditHistoryIntent) -> Unit,
) {
    val canLoadMore = state.hasMore && !state.isLoadingMore && !state.loadMoreFailed && state.items.isNotEmpty()

    // 스크롤 위치를 컴포지션에서 읽으면 프레임마다 다시 그린다. 배치 정보는 효과 안에서만 본다.
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastIndex >= layout.totalItemsCount - 1
        }.distinctUntilChanged()
            .collect { reachedEnd -> if (reachedEnd) onIntent(CreditHistoryIntent.LoadMore) }
    }
}

/** 내 이프 잔액. 친구 초대의 코드 상자와 같은 바탕·여백을 쓴다. */
@Composable
private fun CreditBalanceBox(
    balance: Long?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val balanceHeight =
        with(density) {
            ManyakTheme.typography.titleLarge.fontSize
                .toDp()
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.card)
                .padding(ManyakTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.my_credit_label),
            style = ManyakTheme.typography.bodyMediumStrong,
            color = ManyakTheme.colors.textSubtle,
        )
        if (balance == null) {
            SkeletonPlaceholder(
                alpha = rememberSkeletonPulseAlpha(),
                modifier = Modifier.width(BalanceSkeletonWidth).heightIn(min = balanceHeight),
            )
        } else {
            Text(
                text = remember(balance) { NumberFormat.getInstance().format(balance) },
                style = ManyakTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = ManyakTheme.colors.text,
            )
        }
    }
}

@Composable
private fun EmptyCreditHistory(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.block),
        text = stringResource(R.string.my_credit_history_empty),
        style = ManyakTheme.typography.bodyLargeStrong,
        color = ManyakTheme.colors.textSubtle,
        textAlign = TextAlign.Center,
    )
}

private val BalanceSkeletonWidth = 96.dp

@Preview(showBackground = true, name = "이프 내역")
@Composable
private fun CreditHistoryPreview() {
    ManyakTheme(darkTheme = false) {
        CreditHistoryContent(
            state =
                CreditHistoryUiState(
                    balance = 3160,
                    isLoading = false,
                    items =
                        listOf(
                            CreditTransaction(
                                type = CreditTransactionType.SPEND,
                                reason = CreditTransactionReason.CHAT_TURN,
                                amount = 20,
                                title = "유운잔검기",
                                expiresDate = null,
                                createdDate = "2026-08-31",
                            ),
                            CreditTransaction(
                                type = CreditTransactionType.EARN,
                                reason = CreditTransactionReason.ATTENDANCE_REWARD,
                                amount = 350,
                                title = null,
                                expiresDate = "2026-09-30",
                                createdDate = "2026-08-31",
                            ),
                        ),
                ),
            onIntent = {},
        )
    }
}
