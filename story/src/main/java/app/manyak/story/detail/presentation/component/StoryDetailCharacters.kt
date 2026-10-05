package app.manyak.story.detail.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.component.CHARACTER_IMAGE_ASPECT_RATIO
import app.manyak.designsystem.component.ScrollEdgeFadeHeight
import app.manyak.designsystem.component.StoryOverlayScrim
import app.manyak.designsystem.component.isAllowedCharacterImageUrl
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.detail.presentation.preview.previewCharacters
import app.manyak.story.entity.StoryCharacter
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.story.R as StoryR

/**
 * 주변 인물. 한 번에 한 인물만 크게 보이고, 인물이 둘 이상이면 제목 아래 썸네일 줄로 고른다.
 *
 * 썸네일 줄은 화면 끝까지 스크롤되므로 좌우 여백을 섹션에 걸지 않고 제목·카드가 각자 건다.
 * 고른 인물은 화면 회전 뒤에도 남도록 저장한다.
 */
@Composable
internal fun CharacterSection(
    characters: List<StoryCharacter>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by rememberSaveable(characters) { mutableIntStateOf(0) }
    val selected = characters.getOrElse(selectedIndex) { characters.first() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val gutter = ManyakTheme.spacing.gutter
    val gap = ManyakTheme.spacing.compact

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 화면 폭에 썸네일이 세 장 반 보이게 해, 넷째 인물부터는 잘린 썸네일로 더 있다는 것을 알린다.
        val thumbnailWidth = (maxWidth - gutter * 2 - gap * 3) / VISIBLE_THUMBNAIL_COUNT
        val viewportWidth = maxWidth

        val select: (Int) -> Unit = { index ->
            selectedIndex = index
            // 고른 썸네일을 줄 가운데로 옮겨 양옆 인물이 걸쳐 보이게 한다. 처음과 끝에서는 스크롤 끝에 멈춘다.
            scope.launch {
                val target =
                    with(density) {
                        val itemCenter = gutter + (thumbnailWidth + gap) * index + thumbnailWidth / 2
                        (itemCenter - viewportWidth / 2).toPx()
                    }
                scrollState.animateScrollTo(target.roundToInt().coerceIn(0, scrollState.maxValue))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(gutter)) {
            Text(
                modifier = Modifier.padding(horizontal = gutter),
                text = stringResource(StoryR.string.story_detail_characters),
                style = ManyakTheme.typography.titleMediumStrong,
                color = ManyakTheme.colors.text,
            )
            // 선택 테두리가 잘리지 않도록 줄 위아래에 테두리 두께만큼 여백이 있어, 카드와의 간격은 그만큼 줄인다.
            Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense)) {
                if (characters.size > 1) {
                    CharacterPicker(
                        characters = characters,
                        selectedIndex = selectedIndex,
                        thumbnailWidth = thumbnailWidth,
                        scrollState = scrollState,
                        onSelect = select,
                    )
                }
                CharacterCard(
                    modifier = Modifier.padding(horizontal = gutter),
                    character = selected,
                    onImageClick = onImageClick,
                    onPrevious = (selectedIndex - 1).takeIf { it >= 0 }?.let { { select(it) } },
                    onNext = (selectedIndex + 1).takeIf { it < characters.size }?.let { { select(it) } },
                )
            }
        }
    }
}

/**
 * 인물 썸네일 줄. 고른 썸네일은 바깥 테두리로, 고르지 않은 썸네일은 반투명으로 구분한다.
 * 스크롤할 인물이 남은 쪽 가장자리는 흐리게 지워 줄이 이어진다는 것을 알린다.
 */
@Composable
private fun CharacterPicker(
    characters: List<StoryCharacter>,
    selectedIndex: Int,
    thumbnailWidth: Dp,
    scrollState: ScrollState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = tween<Float>(ManyakTheme.motion.elementEnterMillis)
    val startFade by animateFloatAsState(if (scrollState.canScrollBackward) 1f else 0f, fadeSpec, label = "startFade")
    val endFade by animateFloatAsState(if (scrollState.canScrollForward) 1f else 0f, fadeSpec, label = "endFade")

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // 가장자리를 바탕색으로 덮지 않고 투명하게 지운다 — 썸네일만 흐려지고 배경은 그대로다.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeWidth = ScrollEdgeFadeHeight.toPx()
                    if (startFade > 0f) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = 1f - startFade),
                                    1f to Color.Black,
                                    endX = fadeWidth,
                                ),
                            size = Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    if (endFade > 0f) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0f to Color.Black,
                                    1f to Color.Black.copy(alpha = 1f - endFade),
                                    startX = size.width - fadeWidth,
                                    endX = size.width,
                                ),
                            topLeft = Offset(size.width - fadeWidth, 0f),
                            size = Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                }.horizontalScroll(scrollState)
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.sizes.selectionBorderWidth)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        characters.forEachIndexed { index, character ->
            CharacterThumbnail(
                modifier = Modifier.width(thumbnailWidth),
                character = character,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun CharacterThumbnail(
    character: StoryCharacter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 고를 때와 풀 때가 같은 시간에 진행되게 한 값으로 테두리와 투명도를 함께 움직인다. 테두리가 즉시
    // 나타나고 사라지면 앞 인물의 테두리만 툭 끊기고 새 인물은 흐린 채 테두리부터 생겨 어긋나 보인다.
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(ManyakTheme.motion.screenTransitionMillis),
        label = "thumbnailSelection",
    )
    val shape = ManyakTheme.shapes.thumbnail
    val ringColor = ManyakTheme.colors.text
    val ringWidth = ManyakTheme.sizes.selectionBorderWidth

    Box(
        modifier =
            modifier
                .aspectRatio(CHARACTER_IMAGE_ASPECT_RATIO)
                // 선택 테두리는 썸네일 바깥에 그려 이미지를 가리지 않고, 선택이 바뀌어도 자리가 흔들리지 않게 한다.
                // 이미지의 반투명과 분리해 테두리 자체는 늘 진한 색으로 차오른다.
                .drawWithContent {
                    drawContent()
                    if (selection > 0f) {
                        val stroke = ringWidth.toPx()
                        val radius = shape.topStart.toPx(size, this) + stroke / 2
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-stroke / 2, -stroke / 2),
                            size = Size(size.width + stroke, size.height + stroke),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(stroke),
                            alpha = selection,
                        )
                    }
                }
                // 선택 변화 자체가 반응이라 눌림 리플을 그리지 않는다(키워드 칩과 같다).
                .selectable(
                    selected = selected,
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                ).semantics { contentDescription = character.name },
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = UNSELECTED_THUMBNAIL_ALPHA + (1f - UNSELECTED_THUMBNAIL_ALPHA) * selection
                    }.clip(shape)
                    .border(CardBorderWidth, ManyakTheme.colors.border, shape),
        ) {
            CharacterPicture(imageUrl = character.imageUrl, contentScale = ContentScale.Crop)
        }
    }
}

/**
 * 고른 인물 카드. 제작 방식 선택지 카드와 같은 틀로, 위에 인물 이미지를 두고 아래 구분선 영역에 이름과
 * 소개를 둔다.
 */
@Composable
private fun CharacterCard(
    character: StoryCharacter,
    onImageClick: (String) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = ManyakTheme.colors
    val shape = ManyakTheme.shapes.overlay

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .border(CardBorderWidth, colors.border, shape)
                .background(colors.surfaceRaised)
                .padding(CardBorderWidth),
    ) {
        CharacterCardImage(
            character = character,
            onImageClick = onImageClick,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        HorizontalDivider(thickness = CardBorderWidth, color = colors.border)
        Column(
            modifier =
                Modifier.padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = ManyakTheme.spacing.controlHorizontal,
                ),
        ) {
            Text(text = character.name, style = ManyakTheme.typography.bodyLargeStrong, color = colors.text)
            character.description?.let { description ->
                Text(
                    modifier = Modifier.padding(top = ManyakTheme.spacing.inline),
                    text = description,
                    style =
                        ManyakTheme.typography.bodyMedium.copy(
                            // 한글을 글자 단위가 아니라 어절 경계에서 줄바꿈한다.
                            lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
                            localeList = LocaleList("ko-KR"),
                        ),
                    color = colors.text,
                )
            }
        }
    }
}

/**
 * 카드 위쪽 인물 이미지와 이전·다음 버튼. 이미지가 없거나 불러오지 못하면 표지처럼 기본 심벌을 남긴다 —
 * 인물마다 카드 높이가 달라지면 썸네일을 고를 때마다 아래 내용이 튄다.
 */
@Composable
private fun CharacterCardImage(
    character: StoryCharacter,
    onImageClick: (String) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val imageUrl = character.imageUrl
    var failed by rememberSaveable(imageUrl) { mutableStateOf(false) }
    val viewableUrl = imageUrl?.takeIf { !failed && isAllowedCharacterImageUrl(it) }
    val openLabel = stringResource(DesignsystemR.string.character_image_open, character.name)
    val missingLabel = stringResource(StoryR.string.story_detail_character_image_missing, character.name)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(CHARACTER_IMAGE_ASPECT_RATIO),
    ) {
        CharacterPicture(
            modifier =
                if (viewableUrl == null) {
                    Modifier.semantics { contentDescription = missingLabel }
                } else {
                    Modifier
                        .clickable(role = Role.Button, onClick = { onImageClick(viewableUrl) })
                        .semantics { contentDescription = openLabel }
                },
            imageUrl = viewableUrl,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
        )
        onPrevious?.let {
            CharacterStepButton(
                modifier = Modifier.align(Alignment.CenterStart),
                contentDescription = stringResource(StoryR.string.story_detail_character_previous),
                flipped = true,
                onClick = it,
            )
        }
        onNext?.let {
            CharacterStepButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                contentDescription = stringResource(StoryR.string.story_detail_character_next),
                flipped = false,
                onClick = it,
            )
        }
    }
}

/** 인물 이미지 위 좌우의 이전·다음 버튼. 표지 뱃지와 같은 반투명 바탕이라 밝은 이미지 위에서도 읽힌다. */
@Composable
private fun CharacterStepButton(
    contentDescription: String,
    flipped: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(ManyakTheme.spacing.compact)
                .size(ManyakTheme.sizes.controlSmall)
                .clip(ManyakTheme.shapes.pill)
                .background(StoryOverlayScrim)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon).rotate(if (flipped) 180f else 0f),
            painter = painterResource(DesignsystemR.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ManyakTheme.colors.textInverse,
        )
    }
}

/** 인물 이미지 한 장. 주소가 없거나 불러오지 못하면 표지 placeholder 와 같은 기본 심벌을 그린다. */
@Composable
private fun CharacterPicture(
    imageUrl: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    var failed by rememberSaveable(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize().background(ManyakTheme.colors.backgroundNeutral),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl == null || failed || !isAllowedCharacterImageUrl(imageUrl)) {
            Icon(
                modifier = Modifier.size(PlaceholderSymbolSize),
                painter = painterResource(DesignsystemR.drawable.ic_manyak_symbol),
                contentDescription = null,
                tint = ManyakTheme.colors.textDisabled,
            )
        } else {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = imageUrl,
                contentDescription = null,
                contentScale = contentScale,
                onError = {
                    failed = true
                    onError()
                },
            )
        }
    }
}

private const val VISIBLE_THUMBNAIL_COUNT = 3.5f
private const val UNSELECTED_THUMBNAIL_ALPHA = 0.5f
private val CardBorderWidth = 1.dp
private val PlaceholderSymbolSize = 32.dp

@Preview(showBackground = true, name = "주변 인물")
@Composable
private fun CharacterSectionPreview() {
    ManyakTheme(darkTheme = false) {
        CharacterSection(characters = previewCharacters(), onImageClick = {})
    }
}
