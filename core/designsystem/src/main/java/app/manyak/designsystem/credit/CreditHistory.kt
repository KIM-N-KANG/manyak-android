package app.manyak.designsystem.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.LoadFailedContent
import app.manyak.designsystem.component.LoadMoreFooter
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import app.manyak.designsystem.R as DesignsystemR

/**
 * 이프 내역 목록의 상태별 자리. 이프 충전의 내역 탭과 채팅 메뉴의 이프 내역이 같은 목록을 그린다.
 * 목록 줄은 [row] 가 그린다. 원장 줄을 문구로 바꾸는 일은 이 모듈이 모르는 entity 의 몫이다.
 */
@Suppress("LongParameterList")
fun <T> LazyListScope.creditHistoryItems(
    items: List<T>,
    isLoading: Boolean,
    loadFailed: Boolean,
    isLoadingMore: Boolean,
    loadMoreFailed: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        isLoading -> item { CreditHistorySkeleton() }

        loadFailed ->
            item {
                LoadFailedContent(
                    message = stringResource(DesignsystemR.string.credit_history_load_failed),
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.block),
                )
            }

        items.isEmpty() -> item { EmptyCreditHistory() }

        else -> {
            // 원장 줄에는 안정된 식별자가 없어 key 를 주지 않는다.
            items(items) { item -> row(item) }
            if (isLoadingMore || loadMoreFailed) {
                item { LoadMoreFooter(isLoading = isLoadingMore, onRetry = onLoadMore) }
            }
        }
    }
}

/**
 * 목록 끝에 닿으면 다음 페이지를 잇는다. [enabled] 는 실패한 뒤 거짓이 되어야 한다. 끝에 머무는 동안
 * 같은 실패를 반복하게 되므로 그 자리에서는 재시도 버튼이 이어받는다.
 */
@Composable
fun LoadMoreWhenListEnds(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    // 스크롤 위치를 컴포지션에서 읽으면 프레임마다 다시 그린다. 배치 정보는 효과 안에서만 본다.
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastIndex >= layout.totalItemsCount - 1
        }.distinctUntilChanged()
            .collect { reachedEnd -> if (reachedEnd) currentOnLoadMore() }
    }
}

/** 내역 한 줄. 사유, 대상, 날짜가 왼쪽에, 금액이 오른쪽에 온다. 획득만 강조색이다. */
@Composable
fun CreditHistoryRow(
    label: String,
    subtitle: String?,
    dateLine: String?,
    amount: String,
    isEarned: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(
                text = label,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            dateLine?.let {
                Text(
                    text = it,
                    style = ManyakTheme.typography.bodySmall,
                    color = ManyakTheme.colors.textSubtlest,
                )
            }
        }
        Text(
            text = amount,
            style = ManyakTheme.typography.bodyLargeStrong.copy(fontFeatureSettings = "tnum"),
            color = if (isEarned) ManyakTheme.colors.textBrand else ManyakTheme.colors.text,
        )
    }
}

@Composable
private fun EmptyCreditHistory(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.block),
        text = stringResource(DesignsystemR.string.credit_history_empty),
        style = ManyakTheme.typography.bodyLargeStrong,
        color = ManyakTheme.colors.textSubtle,
        textAlign = TextAlign.Center,
    )
}

/** 조회 중 자리를 잡아 두는 골격. 내역 줄과 같은 구조라 목록이 도착해도 요소가 튀지 않는다. */
@Composable
private fun CreditHistorySkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(DesignsystemR.string.credit_history_loading)
    val alpha = rememberSkeletonPulseAlpha()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // 골격 줄 하나하나가 아니라 목록 전체가 "조회 중"이다.
                .semantics { contentDescription = description },
    ) {
        repeat(PLACEHOLDER_ROW_COUNT) { RowPlaceholder(alpha = alpha) }
    }
}

@Composable
private fun RowPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    // 글줄 높이를 dp 로 박아 두면 시스템 글자 크기를 키웠을 때 골격만 제자리에 남는다.
    val density = LocalDensity.current
    val typography = ManyakTheme.typography
    val labelHeight = with(density) { typography.bodyLargeStrong.fontSize.toDp() }
    val metaHeight = with(density) { typography.bodySmall.fontSize.toDp() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = ManyakTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth(LABEL_WIDTH_FRACTION).heightIn(min = labelHeight),
                alpha = alpha,
            )
            SkeletonPlaceholder(
                modifier = Modifier.width(DateWidth).height(metaHeight),
                alpha = alpha,
            )
        }
        SkeletonPlaceholder(modifier = Modifier.width(AmountWidth).height(labelHeight), alpha = alpha)
    }
}

private const val PLACEHOLDER_ROW_COUNT = 8

private const val LABEL_WIDTH_FRACTION = 0.45f

private val DateWidth = 96.dp

private val AmountWidth = 48.dp
