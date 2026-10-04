package app.manyak.chat.room.presentation.composer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.chat.room.presentation.tour.ChatSpotlightDim
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.roundToInt
import app.manyak.chat.R as ChatR

/** 시트 창 위를 덮는다. 화면 좌표를 사용해 시트와 팝업의 서로 다른 원점을 맞춘다. */
@Composable
internal fun RealtimeImageNudge(
    open: Boolean,
    targetOnScreen: Rect?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = ManyakTheme.motion
    val dimAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec =
            tween(
                if (open) motion.nudgeDimEnterMillis else motion.nudgeExitMillis,
                easing = FastOutSlowInEasing,
            ),
        label = "realtime-image-dim",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = if (open) motion.nudgeCardEnterMillis else motion.nudgeExitMillis,
                delayMillis = if (open) motion.nudgeCardDelayMillis else 0,
            ),
        label = "realtime-image-card",
    )
    if ((!open && dimAlpha == 0f) || targetOnScreen == null) return
    Popup(
        popupPositionProvider = remember { ScreenOriginPositionProvider },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        NudgeContent(open, targetOnScreen, checked, onCheckedChange, onDismiss, dimAlpha, cardAlpha)
    }
}

@Composable
private fun NudgeContent(
    open: Boolean,
    targetOnScreen: Rect,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    dimAlpha: Float,
    cardAlpha: Float,
) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    val title = stringResource(ChatR.string.chat_realtime_image_nudge_title)
    val switchLabel = stringResource(ChatR.string.chat_settings_realtime_image)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .semantics { paneTitle = title },
    ) {
        val offset = origin ?: return@Box
        // 시트 본문은 좌우 16dp다. 8dp씩 넓히면 가장자리 8dp와 안쪽 여백 8dp가 남는다.
        val padding = with(LocalDensity.current) { ManyakTheme.spacing.compact.toPx() }
        val target = targetOnScreen.translate(-offset)
        val highlight = Rect(target.left - padding, target.top, target.right + padding, target.bottom)
        ChatSpotlightDim(highlight, Modifier.graphicsLayer { alpha = dimAlpha })
        NudgePlacement(target, highlight) {
            // 원래 행은 그대로 그린다. 구멍 위의 같은 영역에서 터치와 접근성 스위치를 제공한다.
            Box(
                Modifier
                    .toggleable(
                        value = checked,
                        enabled = open,
                        role = Role.Switch,
                        interactionSource = null,
                        indication = null,
                        onValueChange = onCheckedChange,
                    ).semantics {
                        contentDescription = switchLabel
                    },
            )
            NudgeCard(onDismiss, Modifier.graphicsLayer { alpha = cardAlpha })
        }
    }
}

@Composable
private fun NudgePlacement(
    target: Rect,
    highlight: Rect,
    content: @Composable () -> Unit,
) {
    val cardWidth = ManyakTheme.sizes.tourCardWidth
    val gap = ManyakTheme.spacing.component
    val margin = ManyakTheme.spacing.gutter
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val control = measurables[0].measure(Constraints.fixed(target.width.roundToInt(), target.height.roundToInt()))
        val width = minOf(cardWidth.roundToPx(), constraints.maxWidth - 2 * margin.roundToPx()).coerceAtLeast(0)
        val top = (highlight.bottom + gap.toPx()).roundToInt()
        val card =
            measurables[1].measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    maxHeight = (constraints.maxHeight - top - margin.roundToPx()).coerceAtLeast(0),
                ),
            )
        val left = highlight.left.roundToInt().coerceIn(0, (constraints.maxWidth - width).coerceAtLeast(0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            control.place(target.left.roundToInt(), target.top.roundToInt())
            card.place(left, top)
        }
    }
}

@Composable
private fun NudgeCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.card)
                .pointerInput(Unit) { detectTapGestures {} }
                .background(ManyakTheme.colors.surfaceRaised)
                .verticalScroll(rememberScrollState())
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            stringResource(ChatR.string.chat_realtime_image_nudge_title),
            style = ManyakTheme.typography.bodyLargeStrong,
            color = ManyakTheme.colors.text,
        )
        Text(
            stringResource(ChatR.string.chat_realtime_image_nudge_description),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        Button(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End).padding(top = ManyakTheme.spacing.component),
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(
                stringResource(ChatR.string.chat_realtime_image_nudge_confirm),
                style = ManyakTheme.typography.labelLarge,
            )
        }
    }
}

private object ScreenOriginPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
