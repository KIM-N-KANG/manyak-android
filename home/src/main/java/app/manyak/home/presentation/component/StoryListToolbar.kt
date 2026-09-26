package app.manyak.home.presentation.component

import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.ManyakSelectMenu
import app.manyak.designsystem.component.ManyakSelectOption
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.home.entity.StoryListFilter
import app.manyak.home.entity.StoryListQuery
import app.manyak.home.entity.StoryListSort
import kotlin.math.sign
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 목록 위에 겹쳐 두는 필터·정렬 바. [visible] 이 거짓이면 위로 밀려 자기 영역 밖으로 잘려 나간다.
 *
 * 목록 옆이 아니라 위에 겹치는 이유는 목록 위치를 지키기 위해서다 — 바가 자리를 차지하면 숨고 나타날
 * 때마다 목록이 그 높이만큼 움직인다. 목록은 바 높이만큼의 위 여백을 늘 비워 둔다.
 */
@Composable
internal fun StoryListToolbar(
    query: StoryListQuery,
    visible: Boolean,
    onSelectFilter: (StoryListFilter) -> Unit,
    onSelectSort: (StoryListSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenFraction by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec =
            if (visible) {
                tween(SHOW_MILLIS, easing = ShowEasing)
            } else {
                tween(HIDE_MILLIS, easing = FastOutLinearInEasing)
            },
        label = "storyListToolbar",
    )

    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = -hiddenFraction * size.height }
                    // 숨은 바는 잘려서 눌리지 않지만 낭독·포커스 대상에서도 빼야 한다.
                    .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                    .background(ManyakTheme.colors.surface)
                    // 칩 사이 빈자리를 누르면 밑에 깔린 카드가 눌리지 않게 바가 터치를 받는다.
                    .pointerInput(Unit) {}
                    .padding(vertical = ManyakTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = ManyakTheme.spacing.gutter)
                        .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                StoryListFilter.entries.forEach { filter ->
                    val selected = filter == query.filter
                    FilterChip(
                        label = stringResource(filter.labelRes),
                        selected = selected,
                        // 선택된 칩을 다시 눌러도 해제하지 않는다.
                        onClick = { if (!selected) onSelectFilter(filter) },
                    )
                }
            }
            SortDropdown(
                modifier = Modifier.padding(start = ManyakTheme.spacing.compact, end = ManyakTheme.spacing.compact),
                selected = query.sort,
                onSelect = onSelectSort,
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManyakTheme.colors
    val fill = if (selected) colors.backgroundBrandBold else colors.surfaceRaised
    Box(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.pill)
                .background(fill)
                .border(ChipBorderWidth, if (selected) fill else colors.border, ManyakTheme.shapes.pill)
                // 선택 변화 자체가 반응이라 눌림 리플을 그리지 않는다.
                .selectable(
                    selected = selected,
                    interactionSource = null,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            // 크기·행간이 같은 두 롤이라 선택이 바뀌어도 칩 폭만 굵기만큼 달라진다.
            style = if (selected) ManyakTheme.typography.labelLarge else ManyakTheme.typography.bodyMedium,
            color = if (selected) colors.textInverse else colors.text,
        )
    }
}

@Composable
private fun SortDropdown(
    selected: StoryListSort,
    onSelect: (StoryListSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = StoryListSort.entries.map { sort -> ManyakSelectOption(sort, stringResource(sort.labelRes)) }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = ManyakTheme.sizes.input)
                    .clip(ManyakTheme.shapes.control)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(HomeR.string.home_sort_change),
                        onClick = { expanded = true },
                    ).padding(
                        horizontal = ManyakTheme.spacing.compact,
                        vertical = ManyakTheme.spacing.controlVertical,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            Text(
                text = stringResource(selected.labelRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
            )
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                painter = painterResource(DesignsystemR.drawable.ic_chevron_down),
                contentDescription = null,
                tint = ManyakTheme.colors.textSubtle,
            )
        }
        if (expanded) {
            ManyakSelectMenu(
                options = options,
                selected = selected,
                alignment = Alignment.End,
                onDismiss = { expanded = false },
                onSelect = { sort ->
                    expanded = false
                    if (sort != selected) onSelect(sort)
                },
            )
        }
    }
}

/**
 * 목록 스크롤 방향으로 바를 숨길지 정한다. 한 방향으로 이어진 거리가 숨길 때는 [hideDistancePx],
 * 다시 보일 때는 [showDistancePx] 를 넘어야 바뀐다 — 잔떨림에 깜빡이지 않고, 목록을 조금 되돌려 볼 때
 * 바가 튀어나오지 않게 한다. 목록이 실제로 움직인 양만 세므로 끝에서의 당김은 영향이 없다.
 */
@Stable
internal class ToolbarHideState(
    private val hideDistancePx: Float,
    private val showDistancePx: Float,
) : NestedScrollConnection {
    var hidden by mutableStateOf(false)
        private set

    private var distance = 0f

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // 목록이 아래로 넘어갈 때 consumed.y 가 음수라 부호를 뒤집어 "아래로 간 거리"로 센다.
        val delta = -consumed.y
        if (delta == 0f) return Offset.Zero
        if (delta.sign != distance.sign) distance = 0f
        distance += delta
        when {
            distance > hideDistancePx -> hidden = true
            distance < -showDistancePx -> hidden = false
        }
        return Offset.Zero
    }
}

@Composable
internal fun rememberToolbarHideState(): ToolbarHideState {
    val density = LocalDensity.current
    val hidePx = with(density) { ToolbarHideDistance.toPx() }
    val showPx = with(density) { ToolbarShowDistance.toPx() }
    return remember(hidePx, showPx) { ToolbarHideState(hidePx, showPx) }
}

@get:StringRes
private val StoryListFilter.labelRes: Int
    get() =
        when (this) {
            StoryListFilter.ALL -> HomeR.string.home_filter_all
            StoryListFilter.ORIGINAL -> HomeR.string.home_filter_original
        }

@get:StringRes
private val StoryListSort.labelRes: Int
    get() =
        when (this) {
            StoryListSort.LIKES -> HomeR.string.home_sort_likes
            StoryListSort.LATEST -> HomeR.string.home_sort_latest
            StoryListSort.CHATS -> HomeR.string.home_sort_chats
        }

private val ChipBorderWidth = 1.dp

/** 숨길 때의 한 방향 누적 거리. 잔떨림만 걸러 낸다. 웹과 같은 값이다. */
private val ToolbarHideDistance = 8.dp

/** 다시 보일 때의 한 방향 누적 거리. 목록을 조금 되돌려 보는 정도로는 나오지 않게 숨길 때보다 길다. */
private val ToolbarShowDistance = 32.dp

// 모션은 웹과 같다 — 사라짐은 짧은 가속 곡선, 나타남은 조금 긴 감속 곡선(iOS 시트 곡선)이다.
private const val HIDE_MILLIS = 150
private const val SHOW_MILLIS = 300
private val ShowEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
