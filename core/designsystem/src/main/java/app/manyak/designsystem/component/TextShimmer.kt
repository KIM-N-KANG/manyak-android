package app.manyak.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import app.manyak.designsystem.theme.ManyakTheme

/** 텍스트 전체 폭에 맞춰 왼쪽에서 오른쪽으로 빛의 띠를 움직이는 브러시. */
@Composable
fun rememberTextShimmerBrush(
    base: Color = ManyakTheme.colors.textSubtle,
    highlight: Color = ManyakTheme.colors.text,
    durationMillis: Int = 4_000,
): Brush {
    val transition = rememberInfiniteTransition(label = "writing-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "writing-shimmer-progress",
    )
    val spreadPx = with(LocalDensity.current) { ManyakTheme.sizes.shimmerBandHalfWidth.toPx() }
    return remember(base, highlight, spreadPx, progress) {
        WritingShimmerBrush(base = base, highlight = highlight, spreadPx = spreadPx, progress = progress)
    }
}

/** 띠 중심이 글자 폭 밖(-spread)에서 반대쪽 밖(width+spread)까지 진행도만큼 이동한 사선 그라디언트. */
private class WritingShimmerBrush(
    private val base: Color,
    private val highlight: Color,
    private val spreadPx: Float,
    private val progress: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val center = -spreadPx + (size.width + 2 * spreadPx) * progress
        val middleY = size.height / 2
        val tiltY = spreadPx * BAND_TILT
        return LinearGradientShader(
            from = Offset(center - spreadPx, middleY - tiltY),
            to = Offset(center + spreadPx, middleY + tiltY),
            colors = listOf(base, highlight, base),
        )
    }
}

/** 수평에서 20도 기울인 띠. tan(20°) ≈ 0.364. */
private const val BAND_TILT = 0.364f
