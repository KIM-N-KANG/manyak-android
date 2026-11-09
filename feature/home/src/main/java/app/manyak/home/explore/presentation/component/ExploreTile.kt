package app.manyak.home.explore.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.StoryBadgeScale
import app.manyak.designsystem.component.StoryCover
import app.manyak.designsystem.component.StoryGenreBadge
import app.manyak.designsystem.component.StoryGenreBadges
import app.manyak.designsystem.component.originalTagBackdrop
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 탐색 그리드의 칸 하나. 위에 표지를, 아래에 검은 글 영역의 제목, 한 줄 소개와 장르를 둔다.
 * 낭독기에는 칸 하나를 제목으로 읽는다.
 *
 * 그리드는 칸 순번으로 자리를 지키므로 새로 섞으면 같은 칸에 다른 스토리가 들어온다. 그때 칸 안에서 이전 타일 위에
 * 새 타일을 겹쳐 바꾼다. 새 타일은 표지 그림을 받은 뒤 나타나고, 이전 타일은 새 타일이 다 나타난 뒤에야 걷어 내
 * 빈 칸이 보이지 않는다. 처음 그리는 칸(첫 표시, 다음 페이지, 상세 복귀)은 바로 보인다.
 */
@Composable
internal fun ExploreTile(
    story: StorySummary,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settled by remember { mutableStateOf(story) }
    val incoming = story.takeIf { it.id != settled.id }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ManyakTheme.colors.imageCaption)
                .semantics { contentDescription = story.title }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        // 같은 key 의 타일은 이전 타일이 걷힌 뒤에도 그린 상태 그대로 남아 그림을 다시 받지 않는다.
        listOfNotNull(settled, incoming).forEach { shown ->
            key(shown.id) {
                TileLayer(
                    story = shown,
                    entering = shown === incoming,
                    delayMillis = waveDelayMillis(position),
                    onShown = { settled = shown },
                )
            }
        }
    }
}

@Composable
private fun TileLayer(
    story: StorySummary,
    entering: Boolean,
    delayMillis: Int,
    onShown: () -> Unit,
) {
    val progress = remember { Animatable(if (entering) 0f else 1f) }
    var pictureReady by remember { mutableStateOf(story.thumbnailUrl == null) }
    val currentOnShown by rememberUpdatedState(onShown)
    if (entering) {
        LaunchedEffect(pictureReady) {
            if (!pictureReady) return@LaunchedEffect
            progress.animateTo(1f, tween(TILE_ENTER_MILLIS, delayMillis, TileEasing))
            currentOnShown()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress.value
                    val scale = TILE_START_SCALE + (1f - TILE_START_SCALE) * progress.value
                    scaleX = scale
                    scaleY = scale
                }.background(ManyakTheme.colors.imageCaption),
    ) {
        val scrim = rememberCoverScrim()
        StoryCover(
            thumbnailUrl = story.thumbnailUrl,
            shape = RectangleShape,
            onPictureReady = { pictureReady = true },
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(SCRIM_HEIGHT_FRACTION)
                        .background(scrim),
            )
            if (story.isOriginal) {
                Image(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .width(OriginalTagWidth)
                            .aspectRatio(ORIGINAL_TAG_ASPECT_RATIO)
                            .originalTagBackdrop(backdrop, squareTopStart = true),
                    painter = painterResource(DesignsystemR.drawable.ic_story_original_tag),
                    contentDescription = stringResource(HomeR.string.home_original_tag),
                )
            }
        }
        TileCaption(story = story, modifier = Modifier.overlapAbove(ManyakTheme.spacing.section))
    }
}

/** 표지 아래 검은 글 영역. 소개와 장르는 비어도 자리를 지켜 모든 칸의 높이가 같다. */
@Composable
private fun TileCaption(
    story: StorySummary,
    modifier: Modifier = Modifier,
) {
    val colors = ManyakTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
                .padding(
                    start = ManyakTheme.spacing.component,
                    end = ManyakTheme.spacing.component,
                    bottom = ManyakTheme.spacing.component,
                ),
    ) {
        Text(
            text = story.title,
            style = ManyakTheme.typography.bodyMediumStrong,
            color = colors.textOnImage,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.padding(top = ManyakTheme.spacing.hairline),
            text = story.oneLineIntro,
            style = ManyakTheme.typography.bodySmall,
            color = colors.textOnImageSubtle,
            minLines = INTRO_LINES,
            maxLines = INTRO_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.padding(top = ManyakTheme.spacing.dense)) {
            // 장르가 없어도 뱃지 한 줄 높이를 남긴다. 높이를 dp 로 두면 글자 크기를 바꿨을 때 칸마다 어긋난다.
            StoryGenreBadge(
                modifier = Modifier.alpha(0f),
                text = "",
                scale = StoryBadgeScale.Tiny,
            )
            if (story.genres.isNotEmpty()) {
                StoryGenreBadges(
                    genres = story.genres,
                    scale = StoryBadgeScale.Tiny,
                    containerColor = colors.backgroundOnImage,
                    contentColor = colors.textOnImageSubtle,
                )
            }
        }
    }
}

/**
 * 위 요소 아래쪽 [amount] 만큼 겹쳐 올라간다. 제목 줄이 표지 아래 덮개 위에 놓이고, 칸은 그만큼 짧아진다.
 * Compose 여백은 음수를 받지 않아 배치에서 직접 옮긴다.
 */
private fun Modifier.overlapAbove(amount: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = amount.roundToPx()
        layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
            placeable.place(0, -shift)
        }
    }

/**
 * 표지 아래 절반을 글 영역의 검정으로 잇는 덮개. 직선 그라데이션은 끝에 띠가 보여서, 위로 갈수록 천천히
 * 옅어지는 감속 곡선의 불투명도를 여러 지점에 나눠 표지에 녹아들게 한다. 웹과 같은 지점이다.
 */
@Composable
private fun rememberCoverScrim(): Brush {
    val base = ManyakTheme.colors.imageCaption
    return remember(base) {
        Brush.verticalGradient(
            *ScrimStops
                .map { (fromBottom, alpha) -> (1f - fromBottom) to base.copy(alpha = alpha * base.alpha) }
                .reversed()
                .toTypedArray(),
        )
    }
}

/** (아래에서 잰 위치, 불투명도). */
private val ScrimStops =
    listOf(
        0f to 1f,
        0.19f to 0.738f,
        0.34f to 0.541f,
        0.47f to 0.382f,
        0.565f to 0.278f,
        0.65f to 0.194f,
        0.73f to 0.126f,
        0.802f to 0.075f,
        0.861f to 0.042f,
        0.91f to 0.021f,
        0.952f to 0.008f,
        0.982f to 0.002f,
        1f to 0f,
    )

private const val SCRIM_HEIGHT_FRACTION = 0.5f

private const val INTRO_LINES = 2

/** 조회 중 자리를 잡아 두는 골격. 실제 타일과 같은 표지와 글 영역 높이의 칸이다. */
@Composable
internal fun ExploreGridSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(HomeR.string.home_loading)
    val alpha = rememberSkeletonPulseAlpha()
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        columns = GridCells.Fixed(EXPLORE_COLUMNS),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        userScrollEnabled = false,
    ) {
        items(PLACEHOLDER_TILE_COUNT) {
            Column {
                SkeletonPlaceholder(
                    modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
                    alpha = alpha,
                    shape = RectangleShape,
                )
                Box(modifier = Modifier.overlapAbove(ManyakTheme.spacing.section)) {
                    // 빈 글 영역을 보이지 않게 그려 실제 타일과 같은 높이를 잡고, 그 안에 글줄 자리를 둔다.
                    TileCaption(story = PlaceholderStory, modifier = Modifier.alpha(0f))
                    CaptionPlaceholder(alpha = alpha, modifier = Modifier.matchParentSize())
                }
            }
        }
    }
}

@Composable
private fun CaptionPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.padding(
                horizontal = ManyakTheme.spacing.component,
                vertical = ManyakTheme.spacing.hairline,
            ),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(TitleBarHeight),
            alpha = alpha,
        )
        SkeletonPlaceholder(modifier = Modifier.fillMaxWidth().height(IntroBarHeight), alpha = alpha)
        SkeletonPlaceholder(
            modifier = Modifier.fillMaxWidth(INTRO_LAST_LINE_WIDTH_FRACTION).height(IntroBarHeight),
            alpha = alpha,
        )
        SkeletonPlaceholder(
            modifier = Modifier.width(GenreBarWidth).height(GenreBarHeight),
            alpha = alpha,
            shape = ManyakTheme.shapes.pill,
        )
    }
}

private const val TITLE_WIDTH_FRACTION = 0.75f
private const val INTRO_LAST_LINE_WIDTH_FRACTION = 0.66f
private val TitleBarHeight = 16.dp
private val IntroBarHeight = 12.dp
private val GenreBarWidth = 48.dp
private val GenreBarHeight = 18.dp

private val PlaceholderStory =
    StorySummary(
        id = "",
        title = "",
        authorNickname = null,
        thumbnailUrl = null,
        oneLineIntro = "",
        genres = emptyList(),
        likeCount = 0,
        turnCount = 0,
        createdDate = null,
        isOriginal = false,
    )

/** 왼쪽 위 칸부터 대각선 한 칸마다 늦춰 오른쪽 아래로 퍼지는 물결을 만든다. 상한이 있어 긴 목록에서도 금방 끝난다. */
internal fun waveDelayMillis(position: Int): Int {
    val row = position / EXPLORE_COLUMNS
    val column = position % EXPLORE_COLUMNS
    return minOf((row + column) * WAVE_STEP_MILLIS, WAVE_MAX_DELAY_MILLIS)
}

internal const val EXPLORE_COLUMNS = 2

private const val PLACEHOLDER_TILE_COUNT = 12

private const val TILE_ENTER_MILLIS = 420
private const val WAVE_STEP_MILLIS = 45
private const val WAVE_MAX_DELAY_MILLIS = 450
private const val TILE_START_SCALE = 0.96f

/** 천천히 멈추는 감속 곡선. 새 표지가 제자리에 내려앉는 느낌을 준다. 웹과 같은 곡선이다. */
private val TileEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private const val ORIGINAL_TAG_ASPECT_RATIO = 600f / 216f

/** 웹 탐색 타일과 같은 폭. 칸이 각져 홈 카드처럼 표지 모서리 곡률에 맞출 필요가 없다. */
private val OriginalTagWidth = 72.dp
