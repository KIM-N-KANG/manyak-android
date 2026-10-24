package app.manyak.notice.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.LoadMoreFooter
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.StoryBadgeScale
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.component.withRowListMargins
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notice.domain.isUnread
import app.manyak.notice.entity.NoticeSummary
import kotlinx.coroutines.flow.distinctUntilChanged
import app.manyak.common.R as CommonR
import app.manyak.notice.R as NoticeR

/**
 * 공지사항 목록. 줄마다 제목과 게시일을 두고 게시 30일 안인데 아직 열지 않은 공지는 제목을 굵게 하고 끝에 빨간 점을 단다.
 * 읽음은 상세가 기록하므로 상세에서 돌아오면 그 줄의 점만 꺼진다.
 */
@Composable
fun NoticeListScreen(
    onBack: () -> Unit,
    onOpenNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoticeListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    NoticeListEffect.ShowRefreshFailed ->
                        Toast.makeText(context, CommonR.string.story_refresh_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        NoticeTopBar(onBack = onBack)
        NoticeListContent(
            state = state,
            onOpenNotice = onOpenNotice,
            onIntent = viewModel::onIntent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NoticeListContent(
    state: NoticeListUiState,
    onOpenNotice: (String) -> Unit,
    onIntent: (NoticeListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 금방 끝나는 조회에서 자리만 잡았다 사라지는 깜빡임을 만들지 않는다.
    val showSkeleton = rememberDelayedProgressVisibility(state.isLoading)
    when {
        state.isLoading -> if (showSkeleton) NoticeListSkeleton(modifier = modifier.fillMaxWidth())

        state.loadFailed ->
            LoadFailedContent(
                message = stringResource(NoticeR.string.notice_load_failed),
                onRetry = { onIntent(NoticeListIntent.Retry) },
                modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
            )

        state.notices.isEmpty() ->
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(NoticeR.string.notice_list_empty),
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                )
            }

        else -> NoticeRows(state = state, onOpenNotice = onOpenNotice, onIntent = onIntent, modifier = modifier)
    }
}

@Composable
private fun NoticeRows(
    state: NoticeListUiState,
    onOpenNotice: (String) -> Unit,
    onIntent: (NoticeListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val now = remember(state.notices) { System.currentTimeMillis() }
    LoadMoreWhenListEnds(listState = listState, state = state, onLoadMore = { onIntent(NoticeListIntent.LoadMore) })
    // 당김 새로고침은 그릴 목록이 있을 때만 — 골격·빈 상태·실패 자리는 당길 이유가 없다.
    ManyakPullToRefreshBox(
        modifier = modifier,
        isRefreshing = state.isRefreshing,
        onRefresh = { onIntent(NoticeListIntent.Refresh) },
        contentPadding = PaddingValues(0.dp),
    ) {
        NoticeLazyColumn(
            state = state,
            listState = listState,
            now = now,
            onOpenNotice = onOpenNotice,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun NoticeLazyColumn(
    state: NoticeListUiState,
    listState: LazyListState,
    now: Long,
    onOpenNotice: (String) -> Unit,
    onIntent: (NoticeListIntent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        // 채팅·제작 목록과 같은 리듬 — 위 여백 없이 줄 여백만, 아래는 줄 여백과 같은 값.
        contentPadding = PaddingValues(0.dp).withRowListMargins(),
    ) {
        items(state.notices, key = NoticeSummary::id) { notice ->
            NoticeRow(
                notice = notice,
                isUnread = notice.isUnread(state.readIds, now),
                onClick = { onOpenNotice(notice.id) },
            )
        }
        if (state.isLoadingMore || state.loadMoreFailed) {
            item(key = LOAD_MORE_KEY) {
                LoadMoreFooter(isLoading = state.isLoadingMore, onRetry = { onIntent(NoticeListIntent.LoadMore) })
            }
        }
    }
}

/**
 * 목록 끝에 닿으면 다음 커서를 잇는다. 실패한 뒤에는 자동으로 다시 요청하지 않는다 —
 * 끝에 머무는 동안 같은 실패를 반복하므로 목록 끝의 다시 시도가 이어받는다.
 */
@Composable
private fun LoadMoreWhenListEnds(
    listState: LazyListState,
    state: NoticeListUiState,
    onLoadMore: () -> Unit,
) {
    val canLoadMore = state.hasMore && !state.isLoadingMore && !state.loadMoreFailed && !state.isRefreshing
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastIndex >= layout.totalItemsCount - 1 - LOAD_MORE_AHEAD_ITEMS
        }.distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
}

/** 줄은 테두리 없이 좌우를 채운다. 눌림은 앱의 다른 행과 같은 공통 리플이 말한다. */
@Composable
private fun NoticeRow(
    notice: NoticeSummary,
    isUnread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val newBadge = stringResource(NoticeR.string.notice_new_badge)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        NoticeTagBadge(notice.tag, scale = StoryBadgeScale.Compact)
        Text(
            text =
                buildAnnotatedString {
                    append(notice.title)
                    if (isUnread) {
                        append(' ')
                        appendInlineContent(UNREAD_DOT_ID, newBadge)
                    }
                },
            inlineContent =
                mapOf(
                    UNREAD_DOT_ID to
                        InlineTextContent(
                            Placeholder(UnreadDotSp, UnreadDotSp, PlaceholderVerticalAlign.TextTop),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(ManyakTheme.sizes.unreadDotInline)
                                        .background(ManyakTheme.colors.backgroundDangerBold, ManyakTheme.shapes.pill)
                                        .semantics { contentDescription = newBadge },
                            )
                        },
                ),
            style = if (isUnread) ManyakTheme.typography.bodyLargeStrong else ManyakTheme.typography.bodyLarge,
            color = ManyakTheme.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        notice.publishedAtMillis?.let { publishedAt ->
            Text(
                text = formatNoticeDate(publishedAt),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

@Composable
private fun NoticeListSkeleton(modifier: Modifier = Modifier) {
    val alpha = rememberSkeletonPulseAlpha()
    val loading = stringResource(NoticeR.string.notice_list_loading)
    Column(
        modifier =
            modifier
                .semantics { contentDescription = loading },
    ) {
        repeat(SKELETON_ROWS) { index ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                SkeletonPlaceholder(
                    alpha = alpha,
                    modifier =
                        Modifier
                            .fillMaxWidth(if (index % 3 == 0) SKELETON_TITLE_WIDE else SKELETON_TITLE_NARROW)
                            .height(ManyakTheme.sizes.icon),
                )
                SkeletonPlaceholder(
                    alpha = alpha,
                    modifier = Modifier.fillMaxWidth(SKELETON_DATE_WIDTH).height(ManyakTheme.sizes.iconSmall),
                )
            }
        }
    }
}

private const val LOAD_MORE_KEY = "load-more"
private const val UNREAD_DOT_ID = "unread-dot"
private const val LOAD_MORE_AHEAD_ITEMS = 3
private const val SKELETON_ROWS = 8
private const val SKELETON_TITLE_WIDE = 0.8f
private const val SKELETON_TITLE_NARROW = 0.6f
private const val SKELETON_DATE_WIDTH = 0.25f

/** 인라인 자리는 sp 단위만 받는다. 점 자체는 dp 토큰으로 그린다. */
private val UnreadDotSp = 8.sp

@Preview(showBackground = true)
@Composable
private fun NoticeListPreview() {
    ManyakTheme(darkTheme = false) {
        NoticeListContent(
            state =
                NoticeListUiState(
                    isLoading = false,
                    notices =
                        listOf(
                            NoticeSummary("1", "서비스 점검 안내 (10/27 02:00~04:00)", System.currentTimeMillis()),
                            NoticeSummary("2", "공지사항 기능이 생겼어요", System.currentTimeMillis()),
                        ),
                    readIds = listOf("2"),
                ),
            onOpenNotice = {},
            onIntent = {},
        )
    }
}
