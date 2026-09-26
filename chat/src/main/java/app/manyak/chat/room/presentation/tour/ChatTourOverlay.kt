package app.manyak.chat.room.presentation.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import app.manyak.designsystem.component.ManyakTextButton
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.roundToInt
import app.manyak.chat.R as ChatR

/**
 * 채팅 화면 안내 투어. 딤·대상 하이라이트·안내 카드로 뒤 화면 조작을 막고 컴포저 버튼을 차례로 짚는다.
 *
 * 어느 스텝을 보일지는 대상 버튼이 실제로 그려졌는지에 달려 있어 화면이 정하고, 정한 자리를 [onStepShown]
 * 으로 올린다. 지금 스텝은 ViewModel 이 들고 있어 구성 변경 뒤에도 같은 스텝에서 이어지고 도달 이벤트가
 * 다시 나가지 않는다.
 *
 * @param stepIndex 보이고 있는 스텝. null 이면 막 열려 첫 스텝을 아직 고르지 않았다.
 */
@Composable
internal fun ChatTourOverlay(
    steps: List<ChatTourStep>,
    stepIndex: Int?,
    targets: ChatTourTargets,
    onStepShown: (Int, ChatTourStep) -> Unit,
    onComplete: () -> Unit,
    onSkip: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 대상 좌표는 루트 기준이라 이 상자 기준으로 옮겨야 딤과 카드가 같은 자리를 짚는다.
    var origin by remember { mutableStateOf<Offset?>(null) }
    var viewportHeight by remember { mutableStateOf(0f) }
    val boundsOf: (ChatTourStep) -> Rect? = { step ->
        origin?.let { offset -> targets.stepBounds(step)?.translate(-offset) }
    }
    val goTo: (Int) -> Unit = { from ->
        val next = nextChatTourStep(steps, from, viewportHeight, boundsOf)
        if (next == null) onComplete() else onStepShown(next, steps[next])
    }

    if (stepIndex == null) {
        // 자기 위치를 잰 뒤에야 대상이 화면 안인지 알 수 있다.
        LaunchedEffect(origin != null) { if (origin != null) goTo(0) }
    }
    BackHandler { onSkip(stepIndex ?: 0) }

    val label = stringResource(ChatR.string.chat_tour_label)
    val highlightPadding = with(LocalDensity.current) { ManyakTheme.spacing.dense.toPx() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    origin = coordinates.positionInRoot()
                    viewportHeight = coordinates.size.toSize().height
                }
                // 딤 어디를 눌러도 뒤 화면에 닿지 않는다. 닫기는 건너뛰기·뒤로가기로만 한다.
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics { paneTitle = label },
    ) {
        val step = stepIndex?.let(steps::getOrNull) ?: return@Box
        val highlight = boundsOf(step)?.inflate(highlightPadding) ?: return@Box
        TourDim(highlight)
        TourCardPlacement(highlight) {
            TourCard(
                step = step,
                stepIndex = stepIndex,
                stepCount = steps.size,
                hasNext = nextChatTourStep(steps, stepIndex + 1, viewportHeight, boundsOf) != null,
                onNext = { goTo(stepIndex + 1) },
                onSkip = { onSkip(stepIndex) },
            )
        }
    }
}

/** 하이라이트만 뚫린 딤. 스텝이 바뀌면 구멍이 다음 대상으로 옮겨 간다. */
@Composable
private fun TourDim(highlight: Rect) {
    val hole by animateRectAsState(
        targetValue = highlight,
        animationSpec = tween(ManyakTheme.motion.elementEnterMillis, easing = LinearOutSlowInEasing),
        label = "chat-tour-highlight",
    )
    val scrim = ManyakTheme.colors.tourScrim
    val shape = ManyakTheme.shapes.control
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cutout =
            Path().apply {
                addOutline(shape.createOutline(hole.size, layoutDirection, this@Canvas))
                translate(hole.topLeft)
            }
        val dim = Path().apply { addRect(size.toRect()) }
        drawPath(Path.combine(PathOperation.Difference, dim, cutout), scrim)
    }
}

/**
 * 카드를 하이라이트 아래, 모자라면 위에 둔다. 가로는 하이라이트 중앙이 기본이고 화면을 넘지 않는다.
 * 문구가 길어도 화면 밖으로 밀려나지 않게 놓일 쪽의 남은 높이를 카드의 최대 높이로 준다.
 */
@Composable
private fun TourCardPlacement(
    highlight: Rect,
    card: @Composable () -> Unit,
) {
    val cardWidth = ManyakTheme.sizes.tourCardWidth
    val gapDp = ManyakTheme.spacing.component
    val marginDp = ManyakTheme.spacing.gutter
    Layout(content = card, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val gap = gapDp.toPx()
        val margin = marginDp.toPx()
        val width = minOf(cardWidth.roundToPx(), constraints.maxWidth - 2 * margin.roundToInt()).coerceAtLeast(0)
        val spaceAbove = highlight.top - gap - margin
        val spaceBelow = constraints.maxHeight - highlight.bottom - gap - margin
        // 넓은 쪽 높이로 재면 아래가 넓을 때는 언제나 아래에 들어간다 — 여백이 간격보다 커서다.
        val maxHeight = maxOf(spaceAbove, spaceBelow).roundToInt().coerceAtLeast(0)
        val placeable =
            measurables.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = maxHeight))
        val above = isTourCardAbove(highlight, constraints.maxHeight.toFloat(), placeable.height.toFloat(), gap)
        val y = if (above) highlight.top - gap - placeable.height else highlight.bottom + gap
        val x = tourCardLeft(highlight, placeable.width.toFloat(), constraints.maxWidth.toFloat(), margin)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(x.roundToInt(), y.roundToInt())
        }
    }
}

@Composable
private fun TourCard(
    step: ChatTourStep,
    stepIndex: Int,
    stepCount: Int,
    hasNext: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    // 스텝마다 아래에서 살짝 올라오며 나타난다.
    val entrance = remember(step) { Animatable(0f) }
    val millis = ManyakTheme.motion.elementEnterMillis
    LaunchedEffect(entrance) { entrance.animateTo(1f, tween(millis, easing = LinearOutSlowInEasing)) }
    val rise = ManyakTheme.spacing.compact
    Column(
        modifier =
            Modifier
                .graphicsLayer {
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * rise.toPx()
                }.clip(ManyakTheme.shapes.card)
                .background(ManyakTheme.colors.surfaceRaised)
                .verticalScroll(rememberScrollState())
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            text = stringResource(step.titleRes),
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = stringResource(step.descriptionRes),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.component),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TourStepDots(stepIndex = stepIndex, stepCount = stepCount)
            Spacer(modifier = Modifier.weight(1f))
            ManyakTextButton(onClick = onSkip) {
                Text(
                    text = stringResource(ChatR.string.chat_tour_skip),
                    style = ManyakTheme.typography.labelLarge,
                    color = ManyakTheme.colors.textSubtle,
                )
            }
            Button(
                onClick = onNext,
                shape = ManyakTheme.shapes.control,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = ManyakTheme.colors.brand,
                        contentColor = ManyakTheme.colors.textInverse,
                    ),
            ) {
                Text(
                    text = stringResource(if (hasNext) ChatR.string.chat_tour_next else ChatR.string.chat_tour_done),
                    style = ManyakTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TourStepDots(
    stepIndex: Int,
    stepCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
        repeat(stepCount) { index ->
            Box(
                modifier =
                    Modifier
                        .size(ManyakTheme.sizes.tourStepDot)
                        .clip(ManyakTheme.shapes.pill)
                        .background(
                            if (index == stepIndex) ManyakTheme.colors.textSubtle else ManyakTheme.colors.border,
                        ),
            )
        }
    }
}
