package app.manyak.home.explore.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.STORY_THUMBNAIL_ASPECT_RATIO
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.StoryCover
import app.manyak.designsystem.component.originalTagBackdrop
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 탐색 그리드의 칸 하나. 표지와 오리지널 태그만 두고 제목은 화면 읽기용 이름으로만 둔다.
 *
 * 그리드는 칸 순번으로 자리를 지키므로 새로 섞으면 같은 칸에 다른 스토리가 들어온다. 그때 칸 안에서 이전 표지 위에
 * 새 표지를 겹쳐 바꾼다. 새 표지는 그림을 받은 뒤 나타나고, 이전 표지는 새 표지가 다 나타난 뒤에야 걷어 내
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
                .aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO)
                .background(ManyakTheme.colors.backgroundNeutral)
                .semantics { contentDescription = story.title }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        // 같은 key 의 표지는 이전 표지가 걷힌 뒤에도 그린 상태 그대로 남아 그림을 다시 받지 않는다.
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

    StoryCover(
        thumbnailUrl = story.thumbnailUrl,
        modifier =
            Modifier.graphicsLayer {
                alpha = progress.value
                val scale = TILE_START_SCALE + (1f - TILE_START_SCALE) * progress.value
                scaleX = scale
                scaleY = scale
            },
        shape = RectangleShape,
        onPictureReady = { pictureReady = true },
    ) {
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
}

/** 조회 중 자리를 잡아 두는 골격. 실제 그리드와 같은 세 열, 같은 간격의 표지 자리다. */
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
            SkeletonPlaceholder(
                modifier = Modifier.fillMaxWidth().aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO),
                alpha = alpha,
                shape = RectangleShape,
            )
        }
    }
}

/** 왼쪽 위 칸부터 대각선 한 칸마다 늦춰 오른쪽 아래로 퍼지는 물결을 만든다. 상한이 있어 긴 목록에서도 금방 끝난다. */
internal fun waveDelayMillis(position: Int): Int {
    val row = position / EXPLORE_COLUMNS
    val column = position % EXPLORE_COLUMNS
    return minOf((row + column) * WAVE_STEP_MILLIS, WAVE_MAX_DELAY_MILLIS)
}

internal const val EXPLORE_COLUMNS = 3

private const val PLACEHOLDER_TILE_COUNT = 12

private const val TILE_ENTER_MILLIS = 420
private const val WAVE_STEP_MILLIS = 45
private const val WAVE_MAX_DELAY_MILLIS = 450
private const val TILE_START_SCALE = 0.96f

/** 천천히 멈추는 감속 곡선. 새 표지가 제자리에 내려앉는 느낌을 준다. 웹과 같은 곡선이다. */
private val TileEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private const val ORIGINAL_TAG_ASPECT_RATIO = 600f / 216f

/** 세 열 칸이 홈 카드보다 좁아 태그도 줄인다. */
private val OriginalTagWidth = 56.dp
