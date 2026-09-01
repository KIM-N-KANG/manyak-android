package app.manyak.feature.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 대기 문구 위로 옅은 띠가 왼쪽에서 오른쪽으로 지나가는 텍스트 브러시.
 *
 * 밝은 배경에서는 글자를 20% 알파로 낮춰 배경 쪽으로 옅어지게 하고, 어두운 배경에서는 본문 색까지
 * 밝힌다 — 어두운 배경에서 옅어지는 띠는 배경에 묻혀 보이지 않는다.
 */
@Composable
internal fun rememberWritingShimmerBrush(): Brush {
    val base = ManyakTheme.colors.textSubtlest
    val highlight =
        if (isSystemInDarkTheme()) ManyakTheme.colors.text else base.copy(alpha = LIGHT_HIGHLIGHT_ALPHA)
    val transition = rememberInfiniteTransition(label = "writing-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_DURATION_MILLIS, easing = LinearEasing)),
        label = "writing-shimmer-progress",
    )
    val spreadPx = with(LocalDensity.current) { BandHalfWidth.toPx() }
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

private const val SWEEP_DURATION_MILLIS = 2000

private const val LIGHT_HIGHLIGHT_ALPHA = 0.2f

/** 수평에서 20도 기울인 띠. tan(20°) ≈ 0.364. */
private const val BAND_TILT = 0.364f

private val BandHalfWidth = 60.dp
