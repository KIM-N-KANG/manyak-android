package app.manyak.create.general.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.manyak.create.R
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.theme.MaruBuri
import app.manyak.designsystem.theme.Pretendard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

// 들어올 때는 길게 감속하고 나갈 때는 짧게 가속하며, 누름 반응은 한 번 튀어 오른다. 값은 웹 일러스트와 같다.
private val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val EaseIn = CubicBezierEasing(0.55f, 0f, 0.75f, 0.2f)
private val EaseSpring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// 웹에서 곡선을 따로 적지 않은 전환이 쓰는 CSS 기본 곡선 `ease`와 `ease-out`이다.
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

// 웹 줄 상자처럼 행간 여유를 첫 줄 위와 마지막 줄 아래에도 남기고 위아래로 똑같이 나눈다.
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private const val NO_STORYLINE = -1
private const val SECOND_TAB = 1
private const val WORD_STAGGER_MS = 45
private const val WORD_FADE_MS = 500
private const val WORD_RISE_MS = 600
private const val STILL_WORD_TIME = 60_000f
private const val TOUCH_MOVE_MS = 600
private const val MIN_BLUR_PX = 0.1f
private const val TYPE_BASE_MS = 45L
private const val TYPE_JITTER_MS = 50L
private const val TYPE_SPACE_PAUSE_MS = 60L
private const val TYPE_COMMA_PAUSE_MS = 220L

/** 간편 제작 일러스트. 키워드 세 개를 누르고 스토리라인 단계로 넘어가 두 번째 탭을 본다(한 바퀴 약 11초). */
@Composable
internal fun SimpleCreateIllustration(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val genres = stringArrayResource(R.array.create_method_art_genres).asList()
    val picked = stringArrayResource(R.array.create_method_art_picked_genres).asList()
    val tabs = stringArrayResource(R.array.create_storyline_tab_labels).asList()
    val storylines = stringArrayResource(R.array.create_method_art_storylines).asList()
    val state =
        remember(genres, picked, tabs, storylines, reducedMotion) {
            SimpleArtState(
                picks = picked.map(genres::indexOf),
                chipCount = genres.size,
                tabCount = tabs.size,
                wordCounts = storylines.map { it.split(' ').size },
                still = reducedMotion,
            )
        }
    val colors = ManyakTheme.colors
    ArtSheet(loop = state.takeUnless { reducedMotion }, modifier = modifier) { art ->
        Box(Modifier.fillMaxSize().onPlaced { state.sheet = it }) {
            Row(
                Modifier.fillMaxWidth().padding(top = art.dp(5f), start = art.dp(5.5f), end = art.dp(5.5f)),
                horizontalArrangement = Arrangement.spacedBy(art.dp(1.2f)),
            ) {
                repeat(3) { step ->
                    Box(
                        Modifier.weight(1f).height(art.dp(0.9f)).clip(CircleShape).drawBehind {
                            val filled =
                                when (step) {
                                    0 -> 1f
                                    1 -> state.secondStep.value
                                    else -> 0f
                                }
                            drawRect(colors.border)
                            drawRect(colors.brand, size = size.copy(width = size.width * filled))
                        },
                    )
                }
            }
            val phase =
                Modifier
                    .fillMaxSize()
                    .padding(top = art.dp(9f), start = art.dp(5.5f), end = art.dp(5.5f))
                    .graphicsLayer { alpha = state.phaseAlpha.value }
            KeywordPhase(art, state, genres, phase)
            StorylinePhase(art, state, tabs, storylines, phase)
            TouchIndicator(art, state)
        }
    }
}

/** 일반 제작 일러스트. 제목과 한 줄 소개를 한 글자씩 입력하고 장르와 인물을 하나씩 띄운다. */
@Composable
internal fun GeneralCreateIllustration(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.create_method_art_title)
    val intro = stringResource(R.string.create_method_art_intro)
    val genres = stringArrayResource(R.array.create_method_art_picked_genres).asList()
    val avatars = stringArrayResource(R.array.create_method_art_character_avatars).asList()
    val names = stringArrayResource(R.array.create_method_art_character_names).asList()
    val state =
        remember(title, intro, genres, names, reducedMotion) {
            GeneralArtState(title, intro, genres.size, names.size, reducedMotion)
        }
    val colors = ManyakTheme.colors
    ArtSheet(loop = state.takeUnless { reducedMotion }, modifier = modifier) { art ->
        Column(
            Modifier
                .padding(top = art.dp(5f), start = art.dp(5.5f), end = art.dp(5.5f))
                .graphicsLayer { alpha = state.formAlpha.value },
            verticalArrangement = Arrangement.spacedBy(art.dp(1.8f)),
        ) {
            TextFormField(art, stringResource(R.string.create_method_art_title_label), state.fields[0], state.texts[0])
            TextFormField(art, stringResource(R.string.create_method_art_intro_label), state.fields[1], state.texts[1])
            PopFormField(
                art = art,
                label = stringResource(R.string.create_method_art_genre_label),
                field = state.fields[2],
                group = state.pops[0],
                gap = art.dp(1.6f),
            ) { index, itemModifier -> ArtChip(art, genres[index], on = { 1f }, modifier = itemModifier) }
            PopFormField(
                art = art,
                label = stringResource(R.string.create_method_art_characters_label),
                field = state.fields[3],
                group = state.pops[1],
                gap = art.dp(3f),
            ) { index, itemModifier ->
                // 첫 인물이 주인공이라 아바타를 브랜드 색으로 구분한다.
                val (fill, ink) =
                    if (index ==
                        0
                    ) {
                        colors.backgroundBrandSubtle to colors.textBrand
                    } else {
                        colors.backgroundNeutral to
                            colors.textSubtle
                    }
                Row(
                    itemModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(art.dp(1.4f)),
                ) {
                    Box(
                        Modifier.size(art.dp(6.4f)).background(fill, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(avatars[index], style = art.text(2.8f, FontWeight.Bold, ink))
                    }
                    BasicText(names[index], style = art.text(3.4f, FontWeight.Medium, colors.text))
                }
            }
        }
    }
}

/**
 * 회색 판 위로 앱 화면 시트가 아래로 흘러나가는 구도. 시트 안쪽은 [ArtScale] 단위로 그린다.
 * 장식이라 접근성 트리에서 뺀다.
 */
@Composable
private fun ArtSheet(
    loop: ArtLoop?,
    modifier: Modifier,
    content: @Composable (ArtScale) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (loop != null) {
        LaunchedEffect(loop, lifecycle) { loop.play(ArtTimeline(this, lifecycle)) }
    }
    val colors = ManyakTheme.colors
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(colors.backgroundNeutral)
            .clearAndSetSemantics {},
    ) {
        val art =
            remember(maxWidth, maxHeight, density) { ArtScale(min(maxWidth, maxHeight * 4f / 3f) / 100f, density) }
        val shape = RoundedCornerShape(topStart = art.dp(4f), topEnd = art.dp(4f))
        Box(
            Modifier
                // 시트는 위에서 7u 내려와 아래 끝을 4u 넘겨 잘린다.
                .layout { measurable, constraints ->
                    val width = art.dp(84f).roundToPx()
                    val top = art.dp(7f).roundToPx()
                    val height = constraints.maxHeight - top + art.dp(4f).roundToPx()
                    val sheet = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        sheet.place((constraints.maxWidth - width) / 2, top)
                    }
                }.dropShadow(
                    shape,
                    Shadow(
                        radius = art.dp(8f),
                        color = Color.Black,
                        offset = DpOffset(0.dp, art.dp(3f)),
                        alpha = 0.08f,
                    ),
                ).dropShadow(shape, Shadow(radius = 0.dp, color = Color.Black, spread = art.dp(0.3f), alpha = 0.04f))
                .background(colors.surface, shape),
        ) { content(art) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordPhase(
    art: ArtScale,
    state: SimpleArtState,
    genres: List<String>,
    modifier: Modifier,
) {
    val colors = ManyakTheme.colors
    val part = { index: Int ->
        Modifier.graphicsLayer {
            val gone = state.keywordGone[index].value
            alpha = 1f - gone
            translationX = -art.px(5f) * gone
            with(art) { blur(2.dp * gone) }
        }
    }
    Column(modifier) {
        BasicText(
            stringResource(R.string.create_keyword_title),
            part(0),
            style = art.text(4.6f, FontWeight.Bold, colors.text, lineHeight = 1.35f, letterSpacing = (-0.02f).em),
        )
        BasicText(
            stringResource(R.string.create_method_art_genre_label),
            part(1).padding(top = art.dp(4f), bottom = art.dp(2f)),
            style = art.text(3.2f, FontWeight.Medium, colors.textSubtlest),
        )
        FlowRow(
            part(2),
            horizontalArrangement = Arrangement.spacedBy(art.dp(1.6f)),
            verticalArrangement = Arrangement.spacedBy(art.dp(1.6f)),
        ) {
            genres.forEachIndexed { index, genre ->
                ArtChip(
                    art = art,
                    label = genre,
                    on = { state.chipOn[index].value },
                    modifier =
                        Modifier.onPlaced { state.chips[index] = it }.graphicsLayer {
                            scaleX = state.chipScale[index].value
                            scaleY = state.chipScale[index].value
                        },
                )
            }
        }
    }
}

@Composable
private fun StorylinePhase(
    art: ArtScale,
    state: SimpleArtState,
    tabs: List<String>,
    storylines: List<String>,
    modifier: Modifier,
) {
    val colors = ManyakTheme.colors
    val part = { index: Int ->
        Modifier.graphicsLayer {
            alpha = state.storylineAlpha[index].value
            translationX = art.px(7f) * state.storylineShift[index].value
        }
    }
    Column(modifier) {
        BasicText(
            stringResource(R.string.create_storyline_title),
            part(0),
            style = art.text(4.6f, FontWeight.Bold, colors.text, lineHeight = 1.35f, letterSpacing = (-0.02f).em),
        )
        Row(
            Modifier
                .padding(top = art.dp(3.4f))
                .then(part(1))
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    // 밑줄은 탭 아래 경계선을 덮고 위로 같은 두께만큼 올라온다.
                    val line = art.px(0.3f)
                    val tab = size.width / tabs.size
                    val underline = Size(tab, line * 2)
                    drawRect(colors.border, Offset(0f, size.height - line), Size(size.width, line))
                    val underlineTop = size.height - underline.height
                    drawRect(colors.text, Offset(tab * state.tabPosition.value, underlineTop), underline)
                }.padding(bottom = art.dp(0.3f)),
        ) {
            tabs.forEachIndexed { index, tab ->
                BasicText(
                    tab,
                    Modifier
                        .weight(1f)
                        .onPlaced { if (index == SECOND_TAB) state.secondTab = it }
                        .padding(vertical = art.dp(1.8f)),
                    style = art.text(3.4f, FontWeight.Medium).copy(textAlign = TextAlign.Center),
                    color = { lerp(colors.textSubtle, colors.text, state.tabOn[index].value) },
                )
            }
        }
        StorylineText(art, state, storylines, Modifier.padding(top = art.dp(3.4f)).then(part(2)))
    }
}

/** 스토리라인을 어절 단위로 나눠 생성되듯 차례로 번지게 한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorylineText(
    art: ArtScale,
    state: SimpleArtState,
    storylines: List<String>,
    modifier: Modifier,
) {
    val style =
        art.text(
            size = 4.1f,
            weight = FontWeight.Normal,
            color = ManyakTheme.colors.text,
            lineHeight = 1.75f,
            family = MaruBuri,
            letterSpacing = (-0.02f).em,
        )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 어절 사이는 띄어쓰기 한 칸만큼 띄운다.
    val space =
        remember(style, density) {
            val spaced = measurer.measure("가 가", style).size.width
            val joined = measurer.measure("가가", style).size.width
            with(density) { (spaced - joined).toDp() }
        }
    val words = storylines.getOrNull(state.storyline)?.split(' ').orEmpty()
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space)) {
        words.forEachIndexed { index, word ->
            BasicText(
                word,
                Modifier.graphicsLayer {
                    val elapsed = state.wordTime.value - index * WORD_STAGGER_MS
                    val fade = EaseOut.transform((elapsed / WORD_FADE_MS).coerceIn(0f, 1f))
                    val rise = EaseOut.transform((elapsed / WORD_RISE_MS).coerceIn(0f, 1f))
                    alpha = fade
                    translationY = style.fontSize.toPx() * 0.25f * (1f - rise)
                    with(art) { blur(3.dp * (1f - fade)) }
                },
                style = style,
            )
        }
    }
}

/** 손가락 터치 표시. 시트 기준 좌표의 가운데에 놓인다. */
@Composable
private fun TouchIndicator(
    art: ArtScale,
    state: SimpleArtState,
) {
    val colors = ManyakTheme.colors
    val fill = colors.text.copy(alpha = 0.16f)
    val ring = colors.surface.copy(alpha = 0.7f)
    Box(
        Modifier
            .graphicsLayer {
                val center = state.touchPosition.value
                translationX = center.x - size.width / 2
                translationY = center.y - size.height / 2
                alpha = state.touchAlpha.value
                scaleX = state.touchScale.value
                scaleY = state.touchScale.value
            }.size(art.dp(7f))
            .dropShadow(
                CircleShape,
                Shadow(radius = art.dp(3f), color = Color.Black, offset = DpOffset(0.dp, art.dp(1f)), alpha = 0.12f),
            ).drawBehind {
                val width = art.px(0.5f)
                drawCircle(ring, radius = size.minDimension / 2 + width / 2, style = Stroke(width))
                drawCircle(fill)
            },
    )
}

/** 키워드 칩. 선택되면 채움과 테두리, 글자가 함께 브랜드 색으로 바뀐다. */
@Composable
private fun ArtChip(
    art: ArtScale,
    label: String,
    on: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = ManyakTheme.colors
    BasicText(
        text = label,
        modifier =
            modifier
                .drawBehind {
                    val selected = on()
                    with(art) {
                        drawControl(
                            fill = lerp(colors.surfaceRaised, colors.backgroundBrandSubtle, selected),
                            ring = lerp(colors.border, colors.borderBrand, selected),
                        )
                    }
                }.padding(horizontal = art.dp(3f), vertical = art.dp(1.9f)),
        style = art.text(3.6f, FontWeight.Medium),
        color = { lerp(colors.text, colors.textBrand, on()) },
        maxLines = 1,
        softWrap = false,
    )
}

/** 입력 칸. 입력 중인 칸은 포커스 테두리를 두르고, 비어 있는 동안은 자리 표시 막대를 둔다. */
@Composable
private fun TextFormField(
    art: ArtScale,
    label: String,
    field: FormFieldMotion,
    text: TypedText,
) {
    val colors = ManyakTheme.colors
    Column {
        BasicText(
            label,
            Modifier.padding(bottom = art.dp(1f)),
            style = art.text(3.2f, FontWeight.Medium),
            color = { lerp(colors.textSubtlest, colors.textBrand, field.hint.value) },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(art.dp(10.3f))
                .drawBehind {
                    val focus = field.ring.value
                    with(art) {
                        drawFocusGlow(colors.borderInput, focus)
                        drawControl(colors.surfaceRaised, lerp(colors.border, colors.borderInput, focus))
                    }
                }.clipToBounds()
                .padding(horizontal = art.dp(3.6f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.typed.isNotEmpty() || text.caret) {
                TypedLine(art, text)
            } else if (!field.active) {
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(art.dp(3.6f * 0.7f))
                        .background(colors.backgroundNeutral, RoundedCornerShape(art.dp(1f))),
                )
            }
        }
    }
}

/** 입력한 글과 커서. 칸보다 긴 글은 실제 입력창처럼 앞이 밀려 끝부분이 보인다. */
@Composable
private fun TypedLine(
    art: ArtScale,
    text: TypedText,
) {
    val colors = ManyakTheme.colors
    Row(
        Modifier.layout { measurable, constraints ->
            val line = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            layout(constraints.maxWidth, line.height) { line.place(minOf(0, constraints.maxWidth - line.width), 0) }
        },
    ) {
        BasicText(
            text.typed,
            Modifier.alignByBaseline(),
            style = art.text(3.6f, FontWeight.Normal, colors.text),
            maxLines = 1,
            softWrap = false,
        )
        if (text.caret) {
            // 입력 중에는 커서가 멈춰 있고, 쉬는 동안만 0.5초씩 깜빡인다.
            val visible =
                produceState(true, text.typing) {
                    value = true
                    while (!text.typing) {
                        delay(500)
                        value = !value
                    }
                }
            Box(
                Modifier
                    .padding(start = art.dp(0.4f))
                    .alignBy { it.measuredHeight - art.px(3.6f * 0.12f).toInt() }
                    .size(art.dp(0.5f), art.dp(3.6f))
                    .graphicsLayer { alpha = if (visible.value) 1f else 0f }
                    .background(colors.brand),
            )
        }
    }
}

/** 장르와 인물처럼 항목이 하나씩 떠오르는 칸. 비어 있는 동안은 자리 표시 막대를 둔다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PopFormField(
    art: ArtScale,
    label: String,
    field: FormFieldMotion,
    group: PopGroup,
    gap: Dp,
    item: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val colors = ManyakTheme.colors
    Column {
        BasicText(
            label,
            Modifier.padding(bottom = art.dp(1f)),
            style = art.text(3.2f, FontWeight.Medium),
            color = { lerp(colors.textSubtlest, colors.textBrand, field.hint.value) },
        )
        Box(Modifier.fillMaxWidth().heightIn(min = art.dp(6.4f)), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(art.dp(2.6f))
                    .graphicsLayer { alpha = group.placeholder.value }
                    .background(colors.backgroundNeutral, RoundedCornerShape(art.dp(1f))),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                repeat(group.size) { index ->
                    item(
                        index,
                        Modifier.graphicsLayer {
                            val fade = group.fade[index].value
                            val rise = group.rise[index].value
                            alpha = fade
                            translationY = art.px(1.6f) * (1f - rise)
                            scaleX = 0.88f + 0.12f * rise
                            scaleY = 0.88f + 0.12f * rise
                            with(art) { blur(2.dp * (1f - fade)) }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 일러스트 안쪽 크기 단위 하나. 웹처럼 폭의 1%를 쓰되 카드가 4:3보다 납작해지면 높이 기준으로 바꿔
 * 장면 전체를 같은 비율로 줄인다. 글자도 이 단위를 따라 기기 글자 크기 설정과 무관하게 장면에 맞춘다.
 */
@Immutable
private class ArtScale(
    private val unit: Dp,
    private val density: Density,
) {
    fun dp(times: Float): Dp = unit * times

    fun px(times: Float): Float = with(density) { dp(times).toPx() }

    fun text(
        size: Float,
        weight: FontWeight,
        color: Color = Color.Unspecified,
        lineHeight: Float = 1.5f,
        family: FontFamily = Pretendard,
        letterSpacing: TextUnit = TextUnit.Unspecified,
    ): TextStyle =
        TextStyle(
            color = color,
            fontSize = with(density) { dp(size).toSp() },
            fontWeight = weight,
            fontFamily = family,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight.em,
            lineHeightStyle = CssLineHeight,
        )

    /** 웹의 `filter: blur()`. API 31 미만에서는 흐림 없이 지나간다. */
    fun GraphicsLayerScope.blur(radius: Dp) {
        val px = radius.toPx()
        renderEffect = if (px > MIN_BLUR_PX) BlurEffect(px, px, TileMode.Decal) else null
    }

    /** 입력 중인 칸 바깥에 두르는 반투명 테두리. 포커스가 풀리면 두께와 함께 사라진다. */
    fun DrawScope.drawFocusGlow(
        color: Color,
        focus: Float,
    ) {
        val width = px(times = 0.5f) * focus
        if (width <= 0f) return
        drawRoundRect(
            color = color.copy(alpha = color.alpha * focus / 2),
            topLeft = Offset(-width / 2, -width / 2),
            size = Size(size.width + width, size.height + width),
            cornerRadius = CornerRadius(px(times = 3.6f) + width / 2),
            style = Stroke(width),
        )
    }

    /** 칩과 입력 칸의 채움과 안쪽 테두리. 웹은 inset box-shadow 로 그린다. */
    fun DrawScope.drawControl(
        fill: Color,
        ring: Color,
    ) {
        val radius = px(times = 3.6f)
        val width = px(times = 0.35f)
        drawRoundRect(fill, cornerRadius = CornerRadius(radius))
        drawRoundRect(
            color = ring,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(radius - width / 2),
            style = Stroke(width),
        )
    }
}

/** 일러스트 한 바퀴. 화면에 있는 동안 끝없이 돈다. */
private interface ArtLoop {
    suspend fun play(timeline: ArtTimeline)
}

/**
 * 웹 루프의 대기와 CSS 전환. 전환은 기다리지 않고 흘려보내고, 같은 값에 새 전환이 오면 앞 전환은 끊긴다.
 * 화면이 가려지면 대기가 끝난 뒤 다시 보일 때까지 다음 단계로 넘어가지 않는다.
 */
private class ArtTimeline(
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle,
) {
    suspend fun wait(millis: Long) {
        delay(millis)
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
    }

    /** 바꾼 상태가 한 번 그려진 뒤로 넘어간다. */
    suspend fun frame() {
        repeat(2) { withFrameNanos {} }
    }

    fun go(
        value: Animatable<Float, AnimationVector1D>,
        target: Float,
        millis: Int,
        easing: Easing,
        delayMillis: Int = 0,
    ) {
        scope.launch { value.animateTo(target, tween(millis, delayMillis, easing)) }
    }

    fun move(
        value: Animatable<Offset, AnimationVector2D>,
        target: Offset,
    ) {
        scope.launch { value.animateTo(target, tween(durationMillis = TOUCH_MOVE_MS, easing = EaseOut)) }
    }

    fun later(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

@Stable
private class SimpleArtState(
    private val picks: List<Int>,
    chipCount: Int,
    tabCount: Int,
    private val wordCounts: List<Int>,
    still: Boolean,
) : ArtLoop {
    private val done = if (still) 1f else 0f
    val touchPosition = Animatable(Offset.Zero, Offset.VectorConverter)
    val touchAlpha = Animatable(0f)
    val touchScale = Animatable(1f)
    val chipOn = List(chipCount) { Animatable(if (it in picks) done else 0f) }
    val chipScale = List(chipCount) { Animatable(1f) }
    val secondStep = Animatable(done)
    val phaseAlpha = Animatable(1f)

    /** 키워드 단계의 제목, 라벨, 칩 묶음이 빠진 정도 */
    val keywordGone = List(PHASE_PARTS) { Animatable(done) }

    /** 스토리라인 단계의 제목, 탭, 본문이 들어온 정도와 아직 남은 오른쪽 밀림 */
    val storylineAlpha = List(PHASE_PARTS) { Animatable(done) }
    val storylineShift = List(PHASE_PARTS) { Animatable(1f - done) }
    val tabOn = List(tabCount) { Animatable(if (it == 0) 1f else 0f) }
    val tabPosition = Animatable(0f)
    var storyline by mutableIntStateOf(if (still) 0 else NO_STORYLINE)
        private set
    val wordTime = Animatable(if (still) STILL_WORD_TIME else 0f)
    var sheet: LayoutCoordinates? = null
    val chips = arrayOfNulls<LayoutCoordinates>(chipCount)
    var secondTab: LayoutCoordinates? = null

    override suspend fun play(timeline: ArtTimeline) {
        // 칩 위치는 첫 배치가 끝나야 읽힌다.
        timeline.frame()
        while (true) {
            pickKeywords(timeline)
            switchStoryline(timeline)
            rest(timeline)
        }
    }

    private suspend fun pickKeywords(t: ArtTimeline) {
        touchPosition.snapTo(centerOf(chips[picks.first()]))
        t.frame()
        t.go(touchAlpha, target = 1f, millis = 300, easing = EaseOut)
        for (chip in picks) {
            tap(t, chips[chip], pressedChip = chip) { t.go(chipOn[chip], target = 1f, millis = 200, easing = CssEase) }
        }
        t.wait(millis = 450)
        t.go(touchAlpha, target = 0f, millis = 300, easing = EaseOut)
        // 키워드 단계가 빠르게 빠진 뒤 스토리라인 단계가 들어온다.
        keywordGone.forEachIndexed { i, part ->
            t.go(
                part,
                target = 1f,
                millis = 280,
                easing = EaseIn,
                delayMillis =
                    i * 30,
            )
        }
        for (i in 0 until PHASE_PARTS - 1) {
            t.go(storylineAlpha[i], target = 1f, millis = 500, easing = EaseOut, delayMillis = 120 + i * 60)
            t.go(storylineShift[i], target = 0f, millis = 700, easing = EaseOut, delayMillis = 120 + i * 60)
        }
        // 본문은 자리 이동 없이 투명도만 짧게 바뀐다.
        storylineShift.last().snapTo(0f)
        t.go(storylineAlpha.last(), target = 1f, millis = 200, easing = EaseIn)
        t.go(secondStep, target = 1f, millis = 800, easing = EaseOut)
        t.wait(millis = 300)
        write(t, index = 0)
        t.wait(millis = 2_600)
    }

    private suspend fun switchStoryline(t: ArtTimeline) {
        touchPosition.snapTo(centerOf(secondTab))
        t.frame()
        t.go(touchAlpha, target = 1f, millis = 300, easing = EaseOut)
        t.wait(millis = 350)
        tap(t, secondTab, pressedChip = null) { selectTab(t, SECOND_TAB) }
        t.go(storylineAlpha.last(), target = 0f, millis = 200, easing = EaseIn)
        t.wait(millis = 200)
        t.go(storylineAlpha.last(), target = 1f, millis = 200, easing = EaseIn)
        write(t, index = 1)
        t.wait(millis = 300)
        t.go(touchAlpha, target = 0f, millis = 300, easing = EaseOut)
        t.wait(millis = 2_800)
    }

    private suspend fun tap(
        t: ArtTimeline,
        target: LayoutCoordinates?,
        pressedChip: Int?,
        onTap: () -> Unit,
    ) {
        t.move(touchPosition, centerOf(target))
        t.wait(millis = 620)
        t.go(touchScale, target = 0.72f, millis = 140, easing = CssEaseOut)
        pressedChip?.let { t.go(chipScale[it], target = 0.93f, millis = 120, easing = EaseSpring) }
        t.wait(millis = 130)
        onTap()
        t.go(touchScale, target = 1f, millis = 140, easing = CssEaseOut)
        pressedChip?.let { t.go(chipScale[it], target = 1f, millis = 500, easing = EaseSpring) }
    }

    private fun centerOf(target: LayoutCoordinates?): Offset {
        val root = sheet?.takeIf { it.isAttached }
        val node = target?.takeIf { it.isAttached }
        return if (root != null &&
            node != null
        ) {
            root.localBoundingBoxOf(node, clipBounds = false).center
        } else {
            touchPosition.value
        }
    }

    /** 본문을 바꾸고 다음 프레임부터 어절을 차례로 번지게 한다. 웹처럼 기다리지 않고 흘려보낸다. */
    private suspend fun write(
        t: ArtTimeline,
        index: Int,
    ) {
        wordTime.snapTo(0f)
        storyline = index
        val total = (wordCounts[index] - 1) * WORD_STAGGER_MS + WORD_RISE_MS
        t.later {
            t.frame()
            wordTime.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        }
    }

    private fun selectTab(
        t: ArtTimeline,
        index: Int,
    ) {
        tabOn.forEachIndexed { i, tab ->
            t.go(
                tab,
                target =
                    if (i ==
                        index
                    ) {
                        1f
                    } else {
                        0f
                    },
                millis = 300,
                easing = CssEase,
            )
        }
        t.go(tabPosition, target = index.toFloat(), millis = 550, easing = EaseOut)
    }

    /** 처음 상태로 조용히 되돌린다. 쉬는 박자는 되돌린 뒤에 두어 화면을 열면 바로 시작한다. */
    private suspend fun rest(t: ArtTimeline) {
        t.go(phaseAlpha, target = 0f, millis = 300, easing = EaseIn)
        t.wait(millis = 320)
        (chipOn + keywordGone + storylineAlpha + listOf(secondStep, tabPosition, wordTime)).forEach { it.snapTo(0f) }
        storylineShift.forEach { it.snapTo(1f) }
        tabOn.forEachIndexed { i, tab -> tab.snapTo(if (i == 0) 1f else 0f) }
        storyline = NO_STORYLINE
        t.frame()
        t.go(phaseAlpha, target = 1f, millis = 350, easing = EaseOut)
        t.wait(millis = 500)
    }

    private companion object {
        const val PHASE_PARTS = 3
    }
}

/** 일반 제작 폼 칸 하나의 입력 중 표시. 라벨 색과 칸의 포커스 테두리가 따로 움직인다. */
@Stable
private class FormFieldMotion {
    val hint = Animatable(0f)
    val ring = Animatable(0f)
    var active by mutableStateOf(false)
}

@Stable
private class TypedText(
    val full: String,
    still: Boolean,
) {
    var typed by mutableStateOf(if (still) full else "")
    var caret by mutableStateOf(false)
    var typing by mutableStateOf(false)
}

/** 하나씩 떠오르는 항목 묶음. 첫 항목이 뜨면 자리 표시 막대가 사라진다. */
@Stable
private class PopGroup(
    val size: Int,
    still: Boolean,
) {
    private val shown = if (still) 1f else 0f
    val fade = List(size) { Animatable(shown) }
    val rise = List(size) { Animatable(shown) }
    val placeholder = Animatable(1f - shown)
}

@Stable
private class GeneralArtState(
    title: String,
    intro: String,
    genreCount: Int,
    characterCount: Int,
    still: Boolean,
) : ArtLoop {
    val formAlpha = Animatable(1f)
    val texts = listOf(TypedText(title, still), TypedText(intro, still))
    val pops = listOf(PopGroup(genreCount, still), PopGroup(characterCount, still))
    val fields = List(texts.size + pops.size) { FormFieldMotion() }

    override suspend fun play(timeline: ArtTimeline) {
        // 두 일러스트가 동시에 바뀌지 않도록 박자를 엇갈린다.
        timeline.wait(millis = 300)
        while (true) {
            fields.forEachIndexed { index, field -> fill(timeline, field, index) }
            timeline.wait(millis = 2_800)
            timeline.go(formAlpha, target = 0f, millis = 300, easing = EaseIn)
            timeline.wait(millis = 320)
            texts.forEach { it.typed = "" }
            pops.forEach { group ->
                (group.fade + group.rise).forEach { it.snapTo(0f) }
                group.placeholder.snapTo(1f)
            }
            timeline.frame()
            timeline.go(formAlpha, target = 1f, millis = 350, easing = EaseOut)
            timeline.wait(millis = 700)
        }
    }

    private suspend fun fill(
        t: ArtTimeline,
        field: FormFieldMotion,
        index: Int,
    ) {
        field.active = true
        t.go(field.hint, target = 1f, millis = 300, easing = CssEase)
        t.go(field.ring, target = 1f, millis = 300, easing = EaseOut)
        t.wait(millis = 280)
        val text = texts.getOrNull(index)
        if (text != null) type(t, text) else popIn(t, pops[index - texts.size])
        field.active = false
        t.go(field.hint, target = 0f, millis = 300, easing = CssEase)
        t.go(field.ring, target = 0f, millis = 250, easing = EaseIn)
        t.wait(millis = 120)
    }

    private suspend fun type(
        t: ArtTimeline,
        text: TypedText,
    ) {
        text.caret = true
        text.typing = true
        for (ch in text.full) {
            text.typed += ch
            t.wait(typeDelay(ch))
        }
        text.typing = false
        t.wait(millis = 650)
        text.caret = false
    }

    private suspend fun popIn(
        t: ArtTimeline,
        group: PopGroup,
    ) {
        repeat(group.size) { index ->
            t.go(group.fade[index], target = 1f, millis = 350, easing = EaseOut)
            t.go(group.rise[index], target = 1f, millis = 550, easing = EaseSpring)
            t.go(group.placeholder, target = 0f, millis = 200, easing = CssEase)
            t.wait(millis = 180)
        }
        t.wait(millis = 450)
    }

    /** 사람이 치는 것처럼 글자 간격을 흔들고, 띄어쓰기와 쉼표에서 잠깐 멈춘다. */
    private fun typeDelay(ch: Char): Long =
        TYPE_BASE_MS +
            Random.nextLong(until = TYPE_JITTER_MS) +
            when (ch) {
                ' ' -> TYPE_SPACE_PAUSE_MS
                ',' -> TYPE_COMMA_PAUSE_MS
                else -> 0L
            }
}
