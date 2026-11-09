package app.manyak.home.presentation.component

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.StoryBadgeScale
import app.manyak.designsystem.component.StoryGenreBadge
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 홈 섹션 하나. 이름 아래 가로로 넘기는 카드 줄을 둔다. 카드 두 장이 한 화면에 들고 셋째 카드는 표지
 * 가장자리만 [PeekWidth] 만큼 보여 옆으로 넘길 수 있음을 알린다. 더 보기는 두지 않는다.
 *
 * 카드가 한 화면보다 많으면 이름 줄 끝의 이전, 다음 화살표로도 카드 한 장씩 넘긴다.
 */
@Composable
internal fun HomeSectionRow(
    name: String,
    cardCount: Int,
    modifier: Modifier = Modifier,
    cards: LazyListScope.(cardWidth: Dp) -> Unit,
) {
    val listState = rememberLazyListState()
    val stepper = rememberCardStepper(listState, cardCount)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
        Row(
            modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f).semantics { heading() },
                text = name,
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (cardCount > CARDS_PER_VIEW) {
                Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
                    SectionStepButton(
                        contentDescription = stringResource(HomeR.string.home_section_previous),
                        flipped = true,
                        enabled = listState.canScrollBackward,
                        onClick = { stepper.step(-1) },
                    )
                    SectionStepButton(
                        contentDescription = stringResource(HomeR.string.home_section_next),
                        flipped = false,
                        enabled = listState.canScrollForward,
                        onClick = { stepper.step(1) },
                    )
                }
            }
        }
        SectionCards(state = listState, cards = cards)
    }
}

/**
 * 화살표 한 번에 카드 한 장씩 넘긴다. 넘기는 중에 다시 누르면 지금 위치가 아니라 가던 목적지에서 한 장 더
 * 넘겨, 빠르게 연달아 눌러도 누른 횟수만큼 간다. 손으로 끌어 넘기기가 끼어들면 목적지를 버린다.
 */
private class CardStepper(
    private val listState: LazyListState,
    private val cardCount: Int,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
) {
    private var pendingTarget: Int? = null

    fun step(direction: Int) {
        val target = nextCardIndex(pendingTarget ?: nearestCardIndex(), direction, cardCount)
        pendingTarget = target
        scope.launch {
            try {
                if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
            } finally {
                if (pendingTarget == target) pendingTarget = null
            }
        }
    }

    private fun nearestCardIndex(): Int {
        // 줄 앞 여백 안에 이전 카드가 걸쳐 보이므로 보이는 항목의 첫째가 아니라 firstVisibleItemIndex 를 기준으로 한다.
        val index = listState.firstVisibleItemIndex
        val size =
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }
                ?.size ?: return index
        return index + if (listState.firstVisibleItemScrollOffset * 2 > size) 1 else 0
    }
}

@Composable
private fun rememberCardStepper(
    listState: LazyListState,
    cardCount: Int,
): CardStepper {
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    val reduceMotion =
        remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    return remember(
        listState,
        cardCount,
        scope,
        reduceMotion,
    ) { CardStepper(listState, cardCount, scope, reduceMotion) }
}

/** [from] 카드에서 [direction] 만큼 옮긴 카드 순번. 처음과 끝을 넘지 않는다. */
internal fun nextCardIndex(
    from: Int,
    direction: Int,
    cardCount: Int,
): Int = (from + direction).coerceIn(0, (cardCount - 1).coerceAtLeast(0))

/** 섹션 이름 줄 끝의 이전, 다음 버튼. 넘길 곳이 없는 방향은 흐리게 끈다. */
@Composable
private fun SectionStepButton(
    contentDescription: String,
    flipped: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .alpha(if (enabled) 1f else DISABLED_STEP_ALPHA)
                .size(ManyakTheme.sizes.controlTiny)
                .clip(ManyakTheme.shapes.pill)
                .background(ManyakTheme.colors.backgroundNeutral)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall).rotate(if (flipped) 180f else 0f),
            painter = painterResource(DesignsystemR.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ManyakTheme.colors.text,
        )
    }
}

@Composable
private fun SectionCards(
    state: LazyListState = rememberLazyListState(),
    userScrollEnabled: Boolean = true,
    cards: LazyListScope.(cardWidth: Dp) -> Unit,
) {
    val gutter = ManyakTheme.spacing.gutter
    val cardGap = ManyakTheme.spacing.compact
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 둘째 카드 뒤 간격까지 지나 셋째 카드가 화면 오른쪽 끝에서 PeekWidth 만큼만 보이도록 두 장의 폭을 정한다.
        val cardWidth = (maxWidth - gutter * 2 - PeekWidth) / 2
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(cardGap),
            userScrollEnabled = userScrollEnabled,
        ) {
            cards(cardWidth)
        }
    }
}

/**
 * 조회 중 자리를 잡아 두는 골격. 섹션 두 개와 섹션마다 카드 세 장으로 실제 섹션과 **같은 구조**라
 * 목록이 도착할 때 요소가 튀지 않는다. 표시 여부는 호출부가 지연 판정으로 정한다.
 */
@Composable
internal fun HomeSectionsSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(HomeR.string.home_loading)
    val alpha = rememberSkeletonPulseAlpha()

    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block),
        // 아직 아무것도 없는 자리이므로 스크롤로 더 볼 것이 없다.
        userScrollEnabled = false,
    ) {
        items(PLACEHOLDER_SECTION_COUNT) {
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component)) {
                TextSizedPlaceholder(
                    alpha = alpha,
                    modifier = Modifier.padding(horizontal = ManyakTheme.spacing.gutter).width(SectionNameWidth),
                ) { Text(text = "", style = ManyakTheme.typography.titleLarge) }
                SectionCards(userScrollEnabled = false) { cardWidth ->
                    items(PLACEHOLDER_CARD_COUNT) {
                        CardPlaceholder(alpha = alpha, modifier = Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
            alpha = alpha,
            shape = ManyakTheme.shapes.card,
        )
        Column(modifier = Modifier.padding(horizontal = ManyakTheme.spacing.inline)) {
            TextSizedPlaceholder(alpha = alpha, modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION)) {
                Text(text = "", style = ManyakTheme.typography.bodyLargeStrong)
            }
            // 소개 두 줄 자리 안에 글자 높이의 막대 둘을 가운데 둔다.
            Box(modifier = Modifier.padding(top = ManyakTheme.spacing.hairline)) {
                InvisibleSizer { Text(text = "", style = ManyakTheme.typography.bodyMedium, minLines = INTRO_LINES) }
                val glyphHeight =
                    with(LocalDensity.current) {
                        ManyakTheme.typography.bodyMedium.fontSize
                            .toDp()
                    }
                Column(
                    modifier = Modifier.matchParentSize(),
                    verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense, Alignment.CenterVertically),
                ) {
                    SkeletonPlaceholder(modifier = Modifier.fillMaxWidth().height(glyphHeight), alpha = alpha)
                    SkeletonPlaceholder(
                        modifier = Modifier.fillMaxWidth(INTRO_LAST_LINE_WIDTH_FRACTION).height(glyphHeight),
                        alpha = alpha,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = ManyakTheme.spacing.compact),
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
            ) {
                listOf(GenreWideWidth, GenreNarrowWidth).forEach { width ->
                    TextSizedPlaceholder(alpha = alpha, modifier = Modifier.width(width)) {
                        StoryGenreBadge(text = "", scale = StoryBadgeScale.Compact)
                    }
                }
            }
        }
    }
}

/**
 * [sizer] 를 보이지 않게 그려 실제 글줄이나 뱃지와 같은 높이를 잡고 그 자리를 골격으로 채운다.
 * 높이를 dp 로 박아 두면 글자 크기를 바꿨을 때 골격과 실제 카드의 높이가 어긋나 목록이 도착할 때 튄다.
 */
@Composable
private fun TextSizedPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
    sizer: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        InvisibleSizer(sizer)
        SkeletonPlaceholder(modifier = Modifier.matchParentSize(), alpha = alpha)
    }
}

@Composable
private fun InvisibleSizer(content: @Composable () -> Unit) {
    Box(modifier = Modifier.alpha(0f).clearAndSetSemantics {}) { content() }
}

/** 셋째 카드가 화면 오른쪽 끝에 걸쳐 보이는 폭. */
private val PeekWidth = 16.dp

/** 한 화면에 온전히 보이는 카드 수. 이보다 많아야 화살표를 둔다. */
private const val CARDS_PER_VIEW = 2

private const val DISABLED_STEP_ALPHA = 0.4f

private const val PLACEHOLDER_SECTION_COUNT = 2
private const val PLACEHOLDER_CARD_COUNT = 3

private const val TITLE_WIDTH_FRACTION = 0.75f
private const val INTRO_LAST_LINE_WIDTH_FRACTION = 0.66f

private const val INTRO_LINES = 2

private val SectionNameWidth = 128.dp
private val GenreWideWidth = 48.dp
private val GenreNarrowWidth = 40.dp
