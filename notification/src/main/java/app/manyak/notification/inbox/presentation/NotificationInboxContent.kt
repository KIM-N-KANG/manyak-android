package app.manyak.notification.inbox.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.LoadMoreFooter
import app.manyak.designsystem.component.ManyakPullToRefreshBox
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notification.inbox.entity.NotificationCategory
import kotlinx.coroutines.flow.distinctUntilChanged
import app.manyak.notification.R as NotificationR

@Composable
internal fun NotificationInboxContent(
    state: NotificationInboxUiState,
    onIntent: (NotificationInboxIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    Column(modifier) {
        InboxTabs(state) { onIntent(NotificationInboxIntent.Select(it)) }
        holder.SaveableStateProvider(state.category) {
            InboxList(state, onIntent, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTabs(
    state: NotificationInboxUiState,
    onSelect: (NotificationCategory) -> Unit,
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SecondaryTabRow(
            selectedTabIndex = state.category.ordinal,
            containerColor = ManyakTheme.colors.surface,
            contentColor = ManyakTheme.colors.text,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(state.category.ordinal),
                    height = ManyakTheme.spacing.hairline,
                    color = ManyakTheme.colors.text,
                )
            },
            divider = {
                HorizontalDivider(
                    thickness = ManyakTheme.spacing.hairline,
                    color = ManyakTheme.colors.border,
                )
            },
        ) {
            NotificationCategory.entries.forEach { category ->
                Tab(
                    selected = state.category == category,
                    onClick = { onSelect(category) },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(
                                    if (category ==
                                        NotificationCategory.NEWS
                                    ) {
                                        NotificationR.string.inbox_news
                                    } else {
                                        NotificationR.string.inbox_activity
                                    },
                                ),
                                style = ManyakTheme.typography.labelLarge,
                            )
                            if (state.unread.forCategory(category) >
                                0
                            ) {
                                InboxUnreadDot(stringResource(NotificationR.string.inbox_unread_tab))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun InboxList(
    state: NotificationInboxUiState,
    onIntent: (NotificationInboxIntent) -> Unit,
    modifier: Modifier,
) {
    val page = state.page
    val listState = rememberLazyListState()
    val showSkeleton = rememberDelayedProgressVisibility(page.isLoading && !page.loaded)
    LoadNextPage(listState, page, onIntent)
    when {
        !page.loaded && page.isLoading -> if (showSkeleton) InboxSkeleton(modifier)
        !page.loaded && page.error != null ->
            LoadFailedContent(
                message = stringResource(NotificationR.string.inbox_load_failed),
                onRetry = {
                    onIntent(NotificationInboxIntent.Refresh)
                },
                modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
            )
        page.items.isEmpty() -> InboxEmpty(state.category, modifier)
        else ->
            ManyakPullToRefreshBox(
                isRefreshing = page.isLoading && page.loaded && !page.isLoadingMore,
                onRefresh = { onIntent(NotificationInboxIntent.Refresh) },
                contentPadding = PaddingValues(),
                modifier = modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = ManyakTheme.spacing.compact),
                ) {
                    items(
                        page.items,
                        key = { it.id },
                    ) { item -> NotificationRow(item) { onIntent(NotificationInboxIntent.Open(item)) } }
                    if (page.isLoadingMore || page.error != null) {
                        item {
                            LoadMoreFooter(page.isLoadingMore, onRetry = {
                                onIntent(
                                    if (page.nextCursor !=
                                        null
                                    ) {
                                        NotificationInboxIntent.LoadMore
                                    } else {
                                        NotificationInboxIntent.Refresh
                                    },
                                )
                            })
                        }
                    }
                }
            }
    }
}

@Composable
private fun LoadNextPage(
    listState: LazyListState,
    page: InboxPageState,
    onIntent: (NotificationInboxIntent) -> Unit,
) {
    val canLoad = page.nextCursor != null && !page.isLoading && page.error == null
    LaunchedEffect(listState, canLoad, page.nextCursor) {
        if (canLoad) {
            snapshotFlow {
                val layout = listState.layoutInfo
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 1
            }.distinctUntilChanged().collect { if (it) onIntent(NotificationInboxIntent.LoadMore) }
        }
    }
}

@Composable
private fun InboxEmpty(
    category: NotificationCategory,
    modifier: Modifier,
) {
    val news = category == NotificationCategory.NEWS
    Column(
        modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                if (news) NotificationR.string.inbox_empty_news else NotificationR.string.inbox_empty_activity,
            ),
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(
                if (news) {
                    NotificationR.string.inbox_empty_news_description
                } else {
                    NotificationR.string.inbox_empty_activity_description
                },
            ),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtlest,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InboxSkeleton(modifier: Modifier) {
    val alpha = rememberSkeletonPulseAlpha()
    val loading = stringResource(NotificationR.string.inbox_loading)
    Column(modifier.fillMaxWidth().semantics { contentDescription = loading }) {
        repeat(6) {
            Column(
                Modifier.padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                SkeletonPlaceholder(alpha, Modifier.fillMaxWidth(0.3f).height(ManyakTheme.sizes.iconSmall))
                SkeletonPlaceholder(alpha, Modifier.fillMaxWidth(0.8f).height(ManyakTheme.sizes.icon))
                SkeletonPlaceholder(alpha, Modifier.fillMaxWidth().height(ManyakTheme.sizes.iconSmall))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InboxPreview() {
    ManyakTheme {
        NotificationInboxContent(
            NotificationInboxUiState(
                pages =
                    mapOf(
                        NotificationCategory.NEWS to InboxPageState(loaded = true),
                    ),
            ),
            {},
        )
    }
}
