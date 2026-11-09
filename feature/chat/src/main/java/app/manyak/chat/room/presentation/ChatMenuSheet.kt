package app.manyak.chat.room.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.common.presentation.credit.CreditHistoryUiState
import app.manyak.common.presentation.credit.amountText
import app.manyak.common.presentation.credit.dateLine
import app.manyak.common.presentation.credit.isEarned
import app.manyak.common.presentation.credit.reasonLabel
import app.manyak.common.presentation.credit.subtitle
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakOptionItem
import app.manyak.designsystem.component.ManyakOptionsSheetHeader
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.credit.CreditBalanceCard
import app.manyak.designsystem.credit.CreditHistoryRow
import app.manyak.designsystem.credit.LoadMoreWhenListEnds
import app.manyak.designsystem.credit.creditHistoryItems
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.report.presentation.StoryReportAction
import kotlinx.coroutines.launch
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.report.R as ReportR

/**
 * 헤더 더보기가 여는 채팅 메뉴. 내 이프 카드 → 새 채팅 시작하기 → 공유하기 → 신고하기 → 삭제하기 순서다.
 *
 * 신고·삭제·충전은 다른 화면이나 모달을 여는 항목이라 시트를 먼저 닫는다 — 시트는 별도 창이라
 * 닫지 않고 이동하면 다음 화면 위에 그대로 남는다. 새 채팅·공유는 요청이 끝날 때까지 시트가 남아
 * 진행을 보이고, 그동안은 끌어내려도 닫히지 않는다.
 *
 * 이프 카드의 내역은 시트를 닫지 않고 같은 시트를 이프 내역으로 바꾼다. 내역에서 뒤로가기는 메뉴로 돌아온다.
 *
 * 참조 스토리가 삭제된 방([ChatRoomUiState.hasStory])에는 새 채팅·신고를 두지 않는다.
 */
@Composable
internal fun ChatMenuSheet(
    state: ChatRoomUiState,
    onIntent: (ChatRoomIntent) -> Unit,
    onDelete: () -> Unit,
    onOpenCreditCharge: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slideOffset = with(LocalDensity.current) { ViewSlideOffset.roundToPx() }
    val motion = ManyakTheme.motion

    ManyakBottomSheet(
        modifier = modifier,
        onDismissRequest = {
            // 내역을 연 채 닫았어도 다시 열면 메뉴부터 보인다.
            if (state.creditHistory != null) onIntent(ChatRoomIntent.CreditHistoryClosed)
            onDismiss()
        },
        dismissEnabled = !state.isStartingNewChat && !state.isSharing,
        // 내역 목록을 끝까지 끌다 시트까지 닫히지 않게 한다.
        dismissOnContentDrag = state.creditHistory == null,
    ) {
        // 시트 창의 뒤로가기보다 나중에 등록되어 먼저 받는다. 내역에서만 시트를 닫지 않고 메뉴로 돌아온다.
        BackHandler(enabled = state.creditHistory != null) { onIntent(ChatRoomIntent.CreditHistoryClosed) }

        // 화면이 바뀔 때만 전환한다. 내역 목록이 이어 붙는 것은 같은 화면이다.
        AnimatedContent(
            targetState = state.creditHistory,
            contentKey = { history -> history != null },
            transitionSpec = {
                // 내역은 오른쪽에서, 메뉴 복귀는 왼쪽에서 들어온다. 시트 높이는 기본 크기 전환이 잇는다.
                val from = if (targetState != null) slideOffset else -slideOffset
                (
                    fadeIn(
                        tween(motion.screenSlideMillis),
                    ) + slideInHorizontally(tween(motion.screenSlideMillis)) { from }
                ).togetherWith(fadeOut(tween(motion.elementExitMillis)))
            },
            label = "chatMenuView",
        ) { history ->
            if (history != null) {
                ChatMenuCreditHistory(history = history, onIntent = onIntent)
            } else {
                ChatMenuItems(
                    state = state,
                    onIntent = onIntent,
                    onDelete = onDelete,
                    onOpenCreditCharge = onOpenCreditCharge,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ChatMenuItems(
    state: ChatRoomUiState,
    onIntent: (ChatRoomIntent) -> Unit,
    onDelete: () -> Unit,
    onOpenCreditCharge: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter)) {
        ManyakOptionsSheetHeader(title = stringResource(ChatR.string.chat_room_menu))
        Column(modifier = Modifier.fillMaxWidth()) {
            CreditBalanceCard(
                balance = state.creditBalance,
                onOpenCharge = {
                    onDismiss()
                    onOpenCreditCharge()
                },
                onOpenHistory = { onIntent(ChatRoomIntent.CreditHistoryOpened) },
                containerColor = ManyakTheme.colors.backgroundNeutralRaised,
                modifier = Modifier.padding(bottom = ManyakTheme.spacing.gutter),
            )
            if (state.hasStory) {
                ManyakOptionItem(
                    iconRes = DesignsystemR.drawable.ic_comment_plus,
                    label = stringResource(ChatR.string.chat_room_new_chat),
                    onClick = { onIntent(ChatRoomIntent.NewChatRequested) },
                    inProgress = state.isStartingNewChat,
                )
            }
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_share,
                label = stringResource(ChatR.string.chat_room_share),
                onClick = { onIntent(ChatRoomIntent.ShareRequested) },
                inProgress = state.isSharing,
            )
            if (state.hasStory) {
                ManyakOptionItem(
                    iconRes = DesignsystemR.drawable.ic_alert_triangle,
                    label = stringResource(ReportR.string.story_report_action),
                    onClick = {
                        onDismiss()
                        onIntent(ChatRoomIntent.Report(StoryReportAction.Open))
                    },
                )
            }
            ManyakOptionItem(
                iconRes = DesignsystemR.drawable.ic_delete,
                label = stringResource(ChatR.string.chat_room_delete),
                onClick = {
                    onDismiss()
                    onDelete()
                },
                isDestructive = true,
            )
        }
    }
}

/**
 * 메뉴 안의 이프 내역. 머리글은 제자리에 두고 목록만 스크롤한다. 메뉴와 오갈 때 높이가 크게 바뀌지 않도록
 * 화면 높이의 일정 비율로 고정한다. 잔액은 메뉴의 이프 카드가 보인다.
 */
@Composable
private fun ChatMenuCreditHistory(
    history: CreditHistoryUiState,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    val title = stringResource(ChatR.string.chat_room_menu_credit_history)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LoadMoreWhenListEnds(
        listState = listState,
        enabled = history.canLoadMore,
        onLoadMore = { onIntent(ChatRoomIntent.CreditHistoryLoadMore) },
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(LocalConfiguration.current.screenHeightDp.dp * HISTORY_HEIGHT_FRACTION)
                .semantics { paneTitle = title },
    ) {
        CreditHistoryHeader(
            title = title,
            isRefreshing = history.isRefreshing,
            refreshEnabled = !history.isLoading && !history.isRefreshing,
            onBack = { onIntent(ChatRoomIntent.CreditHistoryClosed) },
            onRefresh = {
                // 새 첫 페이지를 맨 위에서 보이도록 먼저 올린다. 부드럽게 올리면 지나가는 동안 목록 끝이 다시 잡힌다.
                scope.launch {
                    listState.scrollToItem(0)
                    onIntent(ChatRoomIntent.CreditHistoryRefresh)
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
        ) {
            creditHistoryItems(
                items = history.items,
                isLoading = history.isLoading,
                loadFailed = history.loadFailed,
                isLoadingMore = history.isLoadingMore,
                loadMoreFailed = history.loadMoreFailed,
                onRetry = { onIntent(ChatRoomIntent.CreditHistoryRetried) },
                onLoadMore = { onIntent(ChatRoomIntent.CreditHistoryLoadMore) },
            ) { transaction ->
                CreditHistoryRow(
                    label = transaction.reasonLabel(),
                    subtitle = transaction.subtitle(),
                    dateLine = transaction.dateLine(),
                    amount = transaction.amountText(),
                    isEarned = transaction.isEarned,
                )
            }
        }
    }
}

/** 돌아가기, 제목과 새로고침. 두 버튼은 글자와 아이콘을 목록 줄의 양쪽 선에 맞추고 터치 영역은 그대로 둔다. */
@Composable
private fun CreditHistoryHeader(
    title: String,
    isRefreshing: Boolean,
    refreshEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).offset(x = -BackGlyphInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManyakIconButton(
                iconRes = DesignsystemR.drawable.ic_arrow_back,
                contentDescription = stringResource(ChatR.string.chat_room_menu_credit_history_back),
                onClick = onBack,
            )
            Text(
                text = title,
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ManyakTextButton(
            modifier = Modifier.offset(x = TextButtonInset),
            onClick = onRefresh,
            enabled = refreshEnabled,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 새로고침 중에도 문구 자리를 남겨 버튼 폭이 스피너 폭으로 줄지 않게 한다.
                Text(
                    modifier = if (isRefreshing) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
                    text = stringResource(ChatR.string.chat_room_menu_credit_history_refresh),
                    style = ManyakTheme.typography.labelLarge,
                    color = if (refreshEnabled) ManyakTheme.colors.textSubtle else ManyakTheme.colors.textDisabled,
                )
                if (isRefreshing) {
                    val refreshing = stringResource(ChatR.string.chat_room_menu_credit_history_refreshing)
                    ManyakProgressIndicator(
                        modifier =
                            Modifier
                                .size(ManyakTheme.sizes.icon)
                                .semantics { contentDescription = refreshing },
                    )
                }
            }
        }
    }
}

private const val HISTORY_HEIGHT_FRACTION = 0.7f

/** 웹의 내역 전환과 같은 밀려 들어오는 거리. */
private val ViewSlideOffset = 24.dp

/** 기본 아이콘 버튼의 터치 영역과 아이콘 사이 여백. */
private val BackGlyphInset = 12.dp

/** 텍스트 버튼 기본 안쪽 여백. 글자 끝을 목록 줄의 오른쪽 선에 맞춘다. */
private val TextButtonInset = 12.dp
