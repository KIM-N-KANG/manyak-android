package app.manyak.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import app.manyak.designsystem.theme.ManyakTheme

/** 골격 조각 하나. 한 화면의 조각들은 [rememberSkeletonPulseAlpha] 하나를 공유해 함께 맥동시킨다. */
@Composable
fun SkeletonPlaceholder(
    alpha: Float,
    modifier: Modifier = Modifier,
    shape: Shape = ManyakTheme.shapes.pill,
) {
    Box(
        modifier =
            modifier
                .alpha(alpha)
                .clip(shape)
                .background(ManyakTheme.colors.backgroundNeutral),
    )
}

/** 정지한 회색 덩어리는 비어 있는 화면과 구분되지 않아, 옅게 맥동시켜 조회 중임을 드러낸다. */
@Composable
fun rememberSkeletonPulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = PULSE_DURATION_MILLIS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeleton-pulse-alpha",
    )
    return alpha
}

private const val PULSE_MIN_ALPHA = 0.4f
private const val PULSE_DURATION_MILLIS = 700
