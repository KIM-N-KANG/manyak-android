package app.manyak.home.onboarding.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.manyak.analytics.entity.StoryOnboardingDetailSource
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.StoryCover
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 가운데 카드와 양옆 카드 일부가 보이는 무한 순환 캐러셀.
 *
 * 페이지 수를 크게 두고 가운데에서 시작해 양쪽으로 끝이 닿지 않게 한다. 페이지 위치는 [rememberPagerState]
 * 가 저장해 회전과 테마 변경 뒤에도 같은 카드가 가운데에 남는다.
 */
@Composable
internal fun StoryCarousel(
    stories: List<StorySummary>,
    onIntent: (StoryOnboardingIntent) -> Unit,
) {
    val count = stories.size
    val pagerState = rememberPagerState(initialPage = loopStartPage(count)) { loopPageCount(count) }
    val scope = rememberCoroutineScope()
    val latestOnIntent by rememberUpdatedState(onIntent)
    // 정착이 아니라 절반을 넘긴 순간에 바꾼다 — 넘기는 도중 CTA 를 눌러도 보이는 카드로 시작한다.
    LaunchedEffect(pagerState, count) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            latestOnIntent(StoryOnboardingIntent.SelectStory(storyIndex(page, count)))
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardWidth = carouselCardWidth(maxWidth, maxHeight)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
            pageSpacing = ManyakTheme.spacing.component,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.CenterVertically,
        ) { page ->
            val index = storyIndex(page, count)
            val isCenter = page == pagerState.currentPage
            CarouselCard(
                story = stories[index],
                isCenter = isCenter,
                onClick = {
                    if (isCenter) {
                        onIntent(StoryOnboardingIntent.OpenStory(index, StoryOnboardingDetailSource.CARD))
                    } else {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                },
                onOpenDetail = { onIntent(StoryOnboardingIntent.OpenStory(index, StoryOnboardingDetailSource.BUTTON)) },
                modifier =
                    Modifier.carouselPage(shadowShape = ManyakTheme.shapes.featureCard) { pagerState.offsetOf(page) },
            )
        }
    }
}

/**
 * 카드 한 장. 스토리 상세의 주변 인물 카드와 같은 틀로, 위에 표지를 두고 구분선 아래에 제목과 소개를 둔다.
 * 표지를 글자가 가리지 않는다. 그림자는 [carouselPage] 가 드리운다.
 *
 * 옆 카드는 누르면 가운데로 오고, 가운데 카드는 누르면 상세를 연다. 옆 카드의 상세 보기는 누르지 않는다 —
 * 아직 고르지 않은 스토리의 상세로 바로 가면 지금 무엇을 보고 있는지가 흐려진다.
 */
@Composable
private fun CarouselCard(
    story: StorySummary,
    isCenter: Boolean,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManyakTheme.colors
    val shape = ManyakTheme.shapes.featureCard
    val clickLabel =
        stringResource(
            if (isCenter) HomeR.string.story_onboarding_detail else HomeR.string.story_onboarding_select_story,
        )
    Column(
        modifier =
            modifier
                .clip(shape)
                .border(CardBorderWidth, colors.border, shape)
                .background(colors.surfaceRaised)
                .clickable(onClickLabel = clickLabel, onClick = onClick)
                .padding(CardBorderWidth),
    ) {
        // 바깥 카드가 모서리를 깎으므로 표지는 각지게 둔다.
        StoryCover(thumbnailUrl = story.thumbnailUrl, shape = RectangleShape)
        HorizontalDivider(thickness = CardBorderWidth, color = colors.border)
        Column(
            modifier =
                Modifier.padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = ManyakTheme.spacing.controlHorizontal,
                ),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            TitleRow(title = story.title, onOpenDetail = onOpenDetail.takeIf { isCenter })
            // 소개가 짧아도 두 줄 높이를 지켜 카드마다 키가 달라지지 않는다 — 나란히 놓인 카드의 아래 변이 어긋나 보인다.
            Text(
                text = story.oneLineIntro,
                style = ManyakTheme.typography.bodyMedium,
                color = colors.textSubtle,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 제목 한 줄과 오른쪽 "상세 보기". [onOpenDetail] 이 없으면(옆 카드) 그리기만 하고 누름은 카드가 받는다. */
@Composable
private fun TitleRow(
    title: String,
    onOpenDetail: (() -> Unit)?,
) {
    val clickModifier =
        if (onOpenDetail == null) {
            Modifier
        } else {
            Modifier.clip(ManyakTheme.shapes.menuItem).clickable(onClick = onOpenDetail)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier =
                Modifier
                    .padding(start = ManyakTheme.spacing.compact)
                    .then(clickModifier)
                    .padding(horizontal = ManyakTheme.spacing.inline, vertical = ManyakTheme.spacing.hairline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(HomeR.string.story_onboarding_detail),
                style = ManyakTheme.typography.labelSmall,
                color = ManyakTheme.colors.textSubtlest,
            )
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.iconTiny),
                painter = painterResource(DesignsystemR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ManyakTheme.colors.textSubtlest,
            )
        }
    }
}

/** 목록을 받기 전 같은 자리에 둔 카드 골격 세 장. 실제 캐러셀과 같은 크기와 옆 카드 모양을 쓴다. */
@Composable
internal fun CarouselSkeleton() {
    val loading = stringResource(HomeR.string.home_loading)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = loading },
        contentAlignment = Alignment.Center,
    ) {
        val cardWidth = carouselCardWidth(maxWidth, maxHeight)
        val alpha = rememberSkeletonPulseAlpha()
        Row(
            modifier = Modifier.wrapContentWidth(unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
        ) {
            SkeletonOffsets.forEach { offset ->
                SkeletonPlaceholder(
                    alpha = alpha,
                    modifier =
                        Modifier
                            .width(cardWidth)
                            .height(cardWidth / STORY_THUMBNAIL_ASPECT_RATIO + cardTextAreaHeight())
                            .carouselPage { offset },
                    shape = ManyakTheme.shapes.featureCard,
                )
            }
        }
    }
}

/**
 * 가운데에서 멀어진 만큼 줄이고 흐린다. 축소의 기준점을 가운데 쪽 변에 둬 카드 사이 간격이 넘기는 동안
 * 그대로이고, 옆 카드는 화면 가장자리 쪽으로만 줄어든다.
 *
 * 그림자도 이 층의 높이로 그린다. 카드 안에서 `shadow` 로 그리면 옆 카드를 흐리는 반투명 층이 자기 크기
 * 밖의 그림자를 잘라 아래 변이 각지게 끊긴다.
 *
 * @param shadowShape 그림자를 드리울 카드 모양. 없으면(골격) 그림자를 두지 않는다.
 * @param offset 가운데 페이지에서 본 이 페이지의 거리. 양수면 왼쪽이다.
 */
private fun Modifier.carouselPage(
    shadowShape: Shape? = null,
    offset: () -> Float,
): Modifier =
    graphicsLayer {
        if (shadowShape != null) {
            shape = shadowShape
            shadowElevation = CardShadowElevation.toPx()
            ambientShadowColor = CardShadowColor
            spotShadowColor = CardShadowColor
        }
        val signed = offset()
        val distance = signed.absoluteValue.coerceAtMost(1f)
        val scale = lerp(1f, SIDE_CARD_SCALE, distance)
        scaleX = scale
        scaleY = scale
        alpha = lerp(1f, SIDE_CARD_ALPHA, distance)
        transformOrigin = TransformOrigin(if (signed > 0f) 1f else 0f, PIVOT_MIDDLE)
    }

private fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

/**
 * 카드 폭. 화면 폭의 일정 비율이 기본이고, 글자 영역을 뺀 남은 높이에 3:4 표지가 들어가지 않으면 높이에 맞춰
 * 줄인다 — 작은 화면에서도 채팅 시작하기를 밀어내지 않는다.
 */
@Composable
private fun carouselCardWidth(
    maxWidth: Dp,
    maxHeight: Dp,
): Dp {
    val coverHeight = maxHeight - ManyakTheme.spacing.section * 2 - cardTextAreaHeight()
    return minOf(maxWidth * CARD_WIDTH_FRACTION, CardMaxWidth, coverHeight * STORY_THUMBNAIL_ASPECT_RATIO)
}

/** 구분선 아래 글자 영역과 카드 테두리의 높이. 줄 높이를 sp 로 읽어 글자 크기 설정을 따라간다. */
@Composable
private fun cardTextAreaHeight(): Dp {
    val typography = ManyakTheme.typography
    val lines =
        with(LocalDensity.current) {
            typography.bodyLargeStrong.lineHeight.toDp() + typography.bodyMedium.lineHeight.toDp() * 2
        }
    return lines + ManyakTheme.spacing.controlHorizontal * 2 + ManyakTheme.spacing.inline + CardBorderWidth * 3
}

/** 순환에 쓰는 전체 페이지 수. 한 편이면 넘길 것이 없어 순환하지 않는다. */
internal fun loopPageCount(storyCount: Int): Int = if (storyCount > 1) LOOP_PAGE_COUNT else storyCount

/** 첫 스토리가 가운데 오는, 전체 페이지의 가운데 근처 페이지. */
internal fun loopStartPage(storyCount: Int): Int {
    if (storyCount <= 1) return 0
    val middle = LOOP_PAGE_COUNT / 2
    return middle - middle % storyCount
}

internal fun storyIndex(
    page: Int,
    storyCount: Int,
): Int = if (storyCount == 0) 0 else page % storyCount

// ponytail: 가운데에서 한쪽으로 50만 장을 넘기면 끝에 닿는다. 실제로 닿을 일이 없어 경계 재배치를 두지 않는다.
private const val LOOP_PAGE_COUNT = 1_000_000

private const val CARD_WIDTH_FRACTION = 0.76f

/** 태블릿에서 카드 한 장이 화면을 다 차지하지 않게 한다. 휴대폰 세로 화면에서는 폭 비율이 먼저 걸린다. */
private val CardMaxWidth = 360.dp

private const val SIDE_CARD_SCALE = 0.88f
private const val SIDE_CARD_ALPHA = 0.4f
private const val PIVOT_MIDDLE = 0.5f

private val CardBorderWidth = 1.dp

/**
 * 카드 그림자. 높이를 크게 둬 넓게 퍼지게 하고 색을 아주 옅게 눌러, 윤곽 대신 카드 아래가 살짝 가라앉은 정도로만
 * 보이게 한다. 플랫폼이 이 색의 투명도에 그림자 기본 농도를 한 번 더 곱한다.
 */
private val CardShadowElevation = 16.dp
private val CardShadowColor = DefaultShadowColor.copy(alpha = 0.3f)

/** 골격의 왼쪽, 가운데, 오른쪽 카드가 가운데에서 떨어진 거리. 양수가 왼쪽이다. */
private val SkeletonOffsets = listOf(1f, 0f, -1f)
