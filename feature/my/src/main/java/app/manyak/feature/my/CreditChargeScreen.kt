package app.manyak.feature.my

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.entity.credit.CreditTransaction
import app.manyak.common.entity.credit.CreditTransactionReason
import app.manyak.common.entity.credit.CreditTransactionType
import app.manyak.core.ui.R
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import app.manyak.common.R as CommonR

/**
 * 이프 충전.
 *
 * 잔액 상자와 탭 줄은 화면 위에 고정하고 탭 내용만 스크롤한다 — 잔액은 어느 탭에서 무엇을 하든
 * 계속 보여야 하는 값이라, 스크롤을 따라 올려 보내면 출석하러 들어온 사람이 지금 얼마인지 모르는
 * 채로 버튼을 누르게 된다.
 */
@Composable
fun CreditChargeScreen(
    onBack: () -> Unit,
    onOpenInvite: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreditChargeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    // 보상 금액은 효과가 도착할 때 정해져 서식만 나중에 채운다.
    val attendanceClaimed = stringResource(R.string.my_attendance_claimed)
    val attendanceAlready = stringResource(R.string.my_attendance_already)
    val attendanceFailed = stringResource(R.string.my_attendance_failed)
    val refreshFailed = stringResource(CommonR.string.story_refresh_failed)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                val message =
                    when (effect) {
                        CreditChargeEffect.ShowRefreshFailed -> refreshFailed
                        is CreditChargeEffect.AttendanceRewarded -> attendanceClaimed.format(effect.amount)
                        CreditChargeEffect.AttendanceAlreadyDone -> attendanceAlready
                        CreditChargeEffect.AttendanceFailed -> attendanceFailed
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 이프는 채팅·제작에서 줄어들고 여기서 출석으로 늘어난다. 화면이 보일 때마다 잔액을 맞춘다.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(CreditChargeIntent.ScreenShown) }

    // 탭은 목적지가 아니라 화면 상태다. 구성 변경에서만 유지하면 되고 복원할 바깥 맥락이 없다.
    var selectedTab by rememberSaveable { mutableStateOf(CreditChargeTab.FREE) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MyDetailHeader(titleRes = R.string.my_credit_charge_title, onBack = onBack)
        CreditBalanceBox(
            balance = state.balance,
            modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
        )
        CreditChargeTabRow(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
        )
        when (selectedTab) {
            CreditChargeTab.FREE ->
                CreditFreeChargeTab(
                    state = state,
                    onIntent = viewModel::onIntent,
                    onOpenInvite = onOpenInvite,
                    modifier = Modifier.weight(1f),
                )

            CreditChargeTab.HISTORY ->
                CreditHistoryTab(
                    state = state,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

/** 내역 탭. 원장 목록만 담고 잔액은 화면 위 상자가 이미 말한다. */
@Composable
private fun CreditHistoryTab(
    state: CreditChargeUiState,
    onIntent: (CreditChargeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenListEnds(listState = listState, state = state, onIntent = onIntent)

    ManyakPullToRefreshBox(
        modifier = modifier,
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(CreditChargeIntent.Refresh) },
        // 헤더가 목록 위에 따로 있어 표시자를 내려 둘 chrome 이 없다.
        contentPadding = PaddingValues(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    top = ManyakTheme.spacing.compact,
                    bottom = ManyakTheme.spacing.screenBottom,
                ),
        ) {
            creditHistoryBody(state = state, onIntent = onIntent)
        }
    }
}

/** 목록·상태별 자리. 탭 안에 오는 것은 이 셋 중 하나뿐이다. */
private fun LazyListScope.creditHistoryBody(
    state: CreditChargeUiState,
    onIntent: (CreditChargeIntent) -> Unit,
) {
    when {
        state.isLoading -> item { CreditHistorySkeleton() }

        state.loadFailed ->
            item {
                LoadFailedContent(
                    message = stringResource(R.string.my_credit_history_load_failed),
                    onRetry = { onIntent(CreditChargeIntent.Retry) },
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
                        onRetry = { onIntent(CreditChargeIntent.LoadMore) },
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
    state: CreditChargeUiState,
    onIntent: (CreditChargeIntent) -> Unit,
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
            .collect { reachedEnd -> if (reachedEnd) onIntent(CreditChargeIntent.LoadMore) }
    }
}

/** 내 이프 잔액. 라벨이 위, 값이 아래 오른쪽에 온다. */
@Composable
private fun CreditBalanceBox(
    balance: Long?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val balanceHeight =
        with(density) {
            ManyakTheme.typography.headlineSmall.fontSize
                .toDp()
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.card)
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            text = stringResource(R.string.my_credit_label),
            style = ManyakTheme.typography.bodyMediumStrong,
            color = ManyakTheme.colors.textSubtle,
        )
        if (balance == null) {
            SkeletonPlaceholder(
                alpha = rememberSkeletonPulseAlpha(),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .width(BalanceSkeletonWidth)
                        .heightIn(min = balanceHeight),
            )
        } else {
            Text(
                modifier = Modifier.align(Alignment.End),
                text = remember(balance) { NumberFormat.getInstance().format(balance) },
                style = ManyakTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
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

@Preview(showBackground = true, name = "이프 충전 · 내역")
@Composable
private fun CreditHistoryTabPreview() {
    ManyakTheme(darkTheme = false) {
        CreditHistoryTab(
            state =
                CreditChargeUiState(
                    balance = 3230,
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
                                amount = 700,
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
