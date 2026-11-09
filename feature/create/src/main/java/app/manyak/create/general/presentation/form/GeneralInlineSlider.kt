package app.manyak.create.general.presentation.form

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import app.manyak.create.R as CreateR

private const val STOP_COUNT = 10
private const val PRESSED_SCALE_Y = 1.35f
private const val FILL_ALPHA = 0.15f
private const val TICK_ALPHA = 0.25f
private const val DISABLED_ALPHA = 0.5f
private const val TEXT_MAX_WIDTH_FRACTION = 0.4f
private const val TABULAR_NUMBERS = "tnum"

/** 손잡이가 글자 위에서 갈라질 때 위아래 점이 벌어지는 최대 거리 */
private val CapPartDistance = 1.dp

// 강성, 감쇠, 질량으로 정한 스프링을 질량 1 기준의 감쇠비와 강성으로 옮긴다
private fun massSpring(
    stiffness: Float,
    damping: Float,
    mass: Float,
) = spring<Float>(dampingRatio = damping / (2 * sqrt(stiffness * mass)), stiffness = stiffness / mass)

private val GlideSpring = massSpring(stiffness = 700f, damping = 50f, mass = 0.5f)
private val BouncySpring = massSpring(stiffness = 500f, damping = 14f, mass = 0.7f)

/**
 * 트랙 안에 라벨과 값을 함께 보여 주는 슬라이더. 손잡이는 고르게 놓인 멈춤 지점 사이를 손가락 그대로
 * 따라가고, 놓으면 가장 가까운 지점으로 미끄러진다. 글자 위를 지날 때는 막대를 지우고 위아래 점만 남긴다.
 */
@Composable
internal fun GeneralInlineSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    label: String,
    readout: String,
    contentDescription: String,
    valueText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var trackWidth by remember { mutableIntStateOf(0) }
    var labelWidth by remember { mutableIntStateOf(0) }
    var readoutWidth by remember { mutableIntStateOf(0) }
    val layout = rememberInlineSliderLayout(valueRange, trackWidth, labelWidth, readoutWidth)
    val handle = remember { HandleState() }
    val restingX = InlineSliderMath.xOf(layout.stops, value.coerceIn(valueRange))
    LaunchedEffect(restingX, handle.pressed, layout.width) {
        if (!handle.pressed && layout.width > 0f) handle.settle(restingX, animate = !layout.reduceMotion)
    }
    val scaleY =
        animateFloatAsState(
            targetValue = if (handle.pressed && !layout.reduceMotion) PRESSED_SCALE_Y else 1f,
            animationSpec = BouncySpring,
            label = "inlineSliderHandleScale",
        )
    val commit =
        rememberUpdatedState<(Int) -> Unit> { next ->
            val clamped = next.coerceIn(valueRange)
            if (clamped != value) onValueChange(clamped)
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(ManyakTheme.sizes.input)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .onSizeChanged { trackWidth = it.width }
            .background(ManyakTheme.colors.backgroundNeutral, ManyakTheme.shapes.menuItem)
            .drawInlineSlider(layout, handle, restingX, scaleY, ManyakTheme.colors.text)
            .inlineSliderGestures(enabled, handle, rememberUpdatedState(layout), commit)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                stateDescription = valueText
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        current = value.toFloat(),
                        range = valueRange.first.toFloat()..valueRange.last.toFloat(),
                        steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
                    )
                if (enabled) {
                    setProgress { target ->
                        commit.value(target.roundToInt())
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        TrackText(label, ManyakTheme.typography.labelLarge, alignLeft = true) { labelWidth = it }
        TrackText(
            text = readout,
            style = ManyakTheme.typography.bodyMediumStrong.copy(fontFeatureSettings = TABULAR_NUMBERS),
            alignLeft = false,
        ) { readoutWidth = it }
    }
}

@Composable
private fun BoxScope.TrackText(
    text: String,
    style: TextStyle,
    alignLeft: Boolean,
    onWidth: (Int) -> Unit,
) {
    val inset = ManyakTheme.spacing.passage
    Text(
        text = text,
        style = style,
        color = ManyakTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .align(if (alignLeft) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                .absoluteOffset(x = if (alignLeft) inset else -inset)
                .fillMaxWidth(TEXT_MAX_WIDTH_FRACTION)
                .wrapContentWidth(if (alignLeft) AbsoluteAlignment.Left else AbsoluteAlignment.Right)
                .onSizeChanged { onWidth(it.width) },
    )
}

/** 트랙 위 위치를 px 로 푼 값. 손잡이 x 는 막대의 왼쪽 끝을 가리킨다 */
private data class InlineSliderLayout(
    val width: Float,
    val endX: Float,
    val stops: List<InlineSliderStop>,
    val textSpans: List<ClosedFloatingPointRange<Float>>,
    val ticks: List<Float>,
    val cornerRadius: Float,
    val clipInset: Float,
    val fillLead: Float,
    val handleWidth: Float,
    val handleTop: Float,
    val grabRadius: Float,
    val partRamp: Float,
    val capPart: Float,
    val reduceMotion: Boolean,
)

@Composable
private fun rememberInlineSliderLayout(
    range: IntRange,
    width: Int,
    labelWidth: Int,
    readoutWidth: Int,
): InlineSliderLayout {
    val context = LocalContext.current
    val reduceMotion =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    val density = LocalDensity.current
    val spacing = ManyakTheme.spacing
    val shape = ManyakTheme.shapes.menuItem
    val height = ManyakTheme.sizes.input
    return remember(range, width, labelWidth, readoutWidth, reduceMotion, density, spacing, shape, height) {
        with(density) {
            val startX = spacing.compact.toPx()
            val endX = max(startX, width - spacing.component.toPx())
            val textInset = spacing.passage.toPx()
            val stops = InlineSliderMath.stops(range, startX, endX)
            val textSpans =
                listOf(
                    textInset..textInset + labelWidth,
                    width - textInset - readoutWidth..width - textInset,
                )
            InlineSliderLayout(
                width = width.toFloat(),
                endX = endX,
                stops = stops,
                textSpans = textSpans,
                ticks = InlineSliderMath.visibleTicks(stops, textSpans, spacing.hairline.toPx()),
                cornerRadius = shape.topStart.toPx(Size(width.toFloat(), height.toPx()), density),
                clipInset = spacing.hairline.toPx(),
                fillLead = spacing.compact.toPx(),
                handleWidth = spacing.inline.toPx(),
                handleTop = spacing.compact.toPx(),
                grabRadius = spacing.component.toPx(),
                partRamp = spacing.dense.toPx(),
                capPart = if (reduceMotion) 0f else CapPartDistance.toPx(),
                reduceMotion = reduceMotion,
            )
        }
    }
}

/** 끄는 동안은 손가락 위치를 그대로 쓰고, 놓으면 [resting] 이 그 자리에서 스프링으로 이어받는다 */
private class HandleState {
    val resting = Animatable(0f)
    var dragX by mutableStateOf<Float?>(null)
    var pressed by mutableStateOf(false)
    var placed by mutableStateOf(false)

    val x: Float get() = dragX ?: resting.value

    suspend fun settle(
        target: Float,
        animate: Boolean,
    ) {
        dragX?.let { resting.snapTo(it) }
        dragX = null
        if (animate && placed) {
            resting.animateTo(target, GlideSpring)
        } else {
            resting.snapTo(target)
            placed = true
        }
    }
}

private fun Modifier.drawInlineSlider(
    layout: InlineSliderLayout,
    handle: HandleState,
    restingX: Float,
    scaleY: State<Float>,
    color: Color,
): Modifier =
    drawWithCache {
        val radius = CornerRadius(layout.cornerRadius)
        val inset = layout.clipInset
        val window = Path().apply { addRoundRect(RoundRect(inset, 0f, size.width - inset, size.height, radius)) }
        val fillSize = Size(size.width - inset * 2, size.height)
        val dot = layout.handleWidth / 2
        val top = layout.handleTop
        val bottom = size.height - top
        onDrawWithContent {
            if (layout.width == 0f) return@onDrawWithContent drawContent()
            // 첫 배치 전에는 Animatable 이 아직 0 이라 값의 자리에 바로 그린다
            val x = if (handle.placed) handle.x else restingX
            // 폭이 고정된 채움을 안쪽 창 안에서 밀어 오른쪽 끝만 손잡이를 따라가게 한다
            val fillRight = if (x >= layout.endX) size.width - inset else x + layout.fillLead
            clipPath(window) {
                drawRoundRect(color.copy(alpha = FILL_ALPHA), Offset(fillRight - fillSize.width, 0f), fillSize, radius)
            }
            layout.ticks.forEach { drawCircle(color.copy(alpha = TICK_ALPHA), dot, Offset(it + dot, center.y)) }
            drawContent()
            val split = InlineSliderMath.split(x, layout.handleWidth, layout.partRamp, layout.textSpans)
            val capShift = split * layout.capPart
            scale(scaleX = 1f, scaleY = scaleY.value, pivot = Offset(x + dot, center.y)) {
                val stem = Size(layout.handleWidth, bottom - top)
                drawRoundRect(color, Offset(x, top), stem, CornerRadius(dot), alpha = 1f - split)
                drawCircle(color, dot, Offset(x + dot, top + dot - capShift))
                drawCircle(color, dot, Offset(x + dot, bottom - dot + capShift))
            }
        }
    }

/**
 * 가로로 터치 슬롭을 넘어야 끌기를 시작해, 세로로 미는 손가락은 바깥 스크롤에 넘긴다.
 * 손잡이를 잡으면 잡은 지점을 유지하고, 트랙을 누르면 손잡이 가운데가 손가락 아래로 온다.
 */
private fun Modifier.inlineSliderGestures(
    enabled: Boolean,
    handle: HandleState,
    layout: State<InlineSliderLayout>,
    commit: State<(Int) -> Unit>,
): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            val center = layout.value.handleWidth / 2
            val pressX = down.position.x - handle.x
            val grab = if (abs(pressX - center) <= layout.value.grabRadius) pressX else center

            fun follow(change: PointerInputChange) {
                val stops = layout.value.stops
                val x = (change.position.x - grab).coerceIn(stops.first().x, layout.value.endX)
                handle.dragX = x
                commit.value(InlineSliderMath.valueAt(stops, x))
            }

            handle.pressed = true
            try {
                val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                if (drag == null) {
                    // 끌지 않고 뗀 탭만 값을 바꾼다. 바깥 스크롤이 가져간 터치는 그대로 둔다
                    currentEvent.changes
                        .firstOrNull { it.id == down.id && it.changedToUp() }
                        ?.let { commit.value(InlineSliderMath.nearest(layout.value.stops, it.position.x - grab).value) }
                } else {
                    follow(drag)
                    horizontalDrag(drag.id) {
                        follow(it)
                        it.consume()
                    }
                    commit.value(InlineSliderMath.nearest(layout.value.stops, handle.x).value)
                }
            } finally {
                handle.pressed = false
            }
        }
    }

/** 값 하나와 그 값이 놓이는 손잡이 x(px) */
internal data class InlineSliderStop(
    val value: Int,
    val x: Float,
)

/** 손잡이 위치와 값을 오가는 순수 계산 */
internal object InlineSliderMath {
    /** 범위를 열 칸으로 나눠 정수로 반올림하고, 겹친 값을 합친 뒤 남은 지점을 [startX]..[endX] 에 고르게 편다 */
    fun stops(
        range: IntRange,
        startX: Float,
        endX: Float,
    ): List<InlineSliderStop> {
        val span = (range.last - range.first).coerceAtLeast(0)
        val values = List(STOP_COUNT) { range.first + (it * span / (STOP_COUNT - 1f)).roundToInt() }.distinct()
        return values.mapIndexed { index, value ->
            val fraction = if (values.size == 1) 0f else index / values.lastIndex.toFloat()
            InlineSliderStop(value, startX + fraction * (endX - startX))
        }
    }

    /** 이웃한 두 지점 사이를 직선으로 이어 [from] 축의 [point] 를 [to] 축으로 옮긴다 */
    fun mapBetweenStops(
        stops: List<InlineSliderStop>,
        point: Float,
        from: (InlineSliderStop) -> Float,
        to: (InlineSliderStop) -> Float,
    ): Float {
        val upperIndex = stops.indexOfFirst { from(it) >= point }
        val upper = stops[if (upperIndex < 0) stops.lastIndex else upperIndex]
        val lower = stops[(upperIndex - 1).coerceAtLeast(0)]
        val fromSpan = from(upper) - from(lower)
        if (fromSpan == 0f) return to(upper)
        return to(lower) + (point - from(lower)) / fromSpan * (to(upper) - to(lower))
    }

    fun xOf(
        stops: List<InlineSliderStop>,
        value: Int,
    ): Float = mapBetweenStops(stops, value.toFloat(), { it.value.toFloat() }, { it.x })

    fun valueAt(
        stops: List<InlineSliderStop>,
        x: Float,
    ): Int = mapBetweenStops(stops, x, { it.x }, { it.value.toFloat() }).roundToInt()

    /** 거리가 같으면 앞 지점을 고른다 */
    fun nearest(
        stops: List<InlineSliderStop>,
        x: Float,
    ): InlineSliderStop = stops.minBy { abs(it.x - x) }

    /**
     * 손잡이가 글자 구간에 들어선 정도(0..1). 구간 가장자리 [ramp] 안에서 서서히 커져, 막대가 한 프레임에
     * 끊기지 않고 갈라진다.
     */
    fun split(
        x: Float,
        handleWidth: Float,
        ramp: Float,
        textSpans: List<ClosedFloatingPointRange<Float>>,
    ): Float =
        textSpans.maxOfOrNull { span ->
            minOf((x + handleWidth - span.start) / ramp, (span.endInclusive - x) / ramp).coerceIn(0f, 1f)
        } ?: 0f

    /** 글자와 겹치는 눈금은 그리지 않는다. 멈춤 지점 자체는 그대로 남는다 */
    fun visibleTicks(
        stops: List<InlineSliderStop>,
        textSpans: List<ClosedFloatingPointRange<Float>>,
        tolerance: Float,
    ): List<Float> =
        stops
            .map { it.x }
            .filter { x -> textSpans.none { x + tolerance >= it.start && x - tolerance <= it.endInclusive } }
}

@Preview(showBackground = true, name = "분량 배분")
@Composable
private fun GeneralInlineSliderPreview() {
    ManyakTheme(darkTheme = false) {
        var ratio by remember { mutableIntStateOf(3) }
        GeneralInlineSlider(
            value = ratio,
            onValueChange = { ratio = it },
            valueRange = 1..9,
            label = stringResource(CreateR.string.general_ratio_description_part, ratio),
            readout = stringResource(CreateR.string.general_ratio_dialogue_part, 10 - ratio),
            contentDescription = stringResource(CreateR.string.general_ratio),
            valueText = stringResource(CreateR.string.general_ratio_value, ratio, 10 - ratio),
            modifier = Modifier.padding(ManyakTheme.spacing.gutter),
        )
    }
}
