package app.manyak.designsystem.component

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 웹의 점 패턴을 테마 색의 점이 벌어지고 커지는 움직임으로 표현한다. 비율은 이미지가 차지할 자리를 예약한다. */
@Composable
fun ImageGenerationLoading(
    label: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = STORY_THUMBNAIL_ASPECT_RATIO,
    shape: Shape = ManyakTheme.shapes.thumbnail,
) {
    require(aspectRatio.isFinite() && aspectRatio > 0f)
    val transition = rememberInfiniteTransition(label = "image-generation")
    val horizontalPhase by transition.generationPhase(durationMillis = 10_681)
    val verticalPhase by transition.generationPhase(durationMillis = 13_195)
    val color = ManyakTheme.colors.text
    val gap = ManyakTheme.sizes.generationDotGap
    val radius = ManyakTheme.sizes.generationDotRadius
    val displacement = ManyakTheme.sizes.generationDotDisplacement
    Canvas(
        modifier =
            modifier
                .aspectRatio(aspectRatio)
                .clip(shape)
                .background(ManyakTheme.colors.backgroundNeutral)
                .border(radius, ManyakTheme.colors.border, shape)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
    ) {
        val gapPx = gap.toPx()
        val radiusPx = radius.toPx()
        val displacementPx = displacement.toPx()
        val focus =
            Offset(
                size.width * (0.5f + sin(horizontalPhase) * 0.12f),
                size.height * (0.5f + cos(verticalPhase) * 0.1f),
            )
        val influenceRadius = size.minDimension * 0.38f
        val columns = (size.width / gapPx).toInt()
        val rows = (size.height / gapPx).toInt()
        val offsetX = (size.width - columns * gapPx) / 2
        val offsetY = (size.height - rows * gapPx) / 2
        for (row in 0..rows) {
            for (column in 0..columns) {
                val x = offsetX + column * gapPx
                val y = offsetY + row * gapPx
                val delta = Offset(x, y) - focus
                val distance = hypot(delta.x, delta.y)
                val influence = generationDotInfluence(distance, influenceRadius)
                val shift = influence * influence * displacementPx
                val direction = if (distance > 0f) delta / distance else Offset.Zero
                drawCircle(
                    color = color,
                    radius = radiusPx * (0.65f + influence * 0.85f),
                    center = Offset(x, y) + direction * shift,
                    alpha = 0.17f + influence * 0.72f,
                )
            }
        }
    }
}

@Composable
private fun InfiniteTransition.generationPhase(durationMillis: Int): State<Float> =
    animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "image-generation-phase-$durationMillis",
    )

/** 영향권 가장자리의 기울기도 0으로 이어 점이 갑자기 움직이기 시작하지 않게 한다. */
internal fun generationDotInfluence(
    distance: Float,
    radius: Float,
): Float {
    if (radius <= 0f) return 0f
    val proximity = (1f - distance / radius).coerceIn(0f, 1f)
    return proximity * proximity * (SMOOTHSTEP_COEFFICIENT - 2f * proximity)
}

private const val FULL_TURN = 6.2831855f

private const val SMOOTHSTEP_COEFFICIENT = 3f
