package app.manyak.root

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import app.manyak.designsystem.R as DesignsystemR

/**
 * 로고 심벌이 제자리에서 통통 튀는 로딩 표시. [bouncing] 이 꺼지면 하던 점프를 마저 착지하고 멈춘다 —
 * 공중에서 그대로 굳으면 멈춘 것이 아니라 고장 난 것처럼 보인다.
 *
 * 심벌은 눈이 따로 움직여야 해서 drawable 대신 같은 경로를 직접 그린다(ic_logo_manyak 의 심벌과 같은 값).
 */
@Composable
internal fun BouncingLogo(
    bouncing: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = ManyakTheme.colors.brand
    val symbolSize = ManyakTheme.sizes.startupSymbol
    val description = stringResource(DesignsystemR.string.app_logo_description)
    val body = remember { PathParser().parsePathString(SYMBOL_PATH).toPath() }
    val hop = rememberHop(bouncing)
    val blink by rememberBlink()

    Canvas(
        modifier =
            modifier
                .size(width = symbolSize, height = symbolSize * (1f + HOP_HEIGHT_RATIO))
                .semantics {
                    contentDescription = description
                    if (bouncing) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                },
    ) {
        val t = hop.value
        val squash = if (t < SQUASH_FRACTION) sin(PI.toFloat() * t / SQUASH_FRACTION) else 0f
        val air = if (t < SQUASH_FRACTION) 0f else (t - SQUASH_FRACTION) / (1f - SQUASH_FRACTION)
        val height = 4f * air * (1f - air) // 0 바닥 … 1 꼭대기
        val stretch = if (air > 0f) STRETCH * abs(1f - 2f * air) else 0f
        val unit = size.width / VIEWPORT
        val groundY = size.height - (VIEWPORT - SYMBOL_BOTTOM) * unit

        // 그림자: 높이 뜰수록 작고 옅어진다.
        val shadowWidth = size.width * 0.6f * (1f - 0.45f * height) * (1f + 0.15f * squash)
        drawOval(
            color = color.copy(alpha = 0.18f * (1f - 0.6f * height)),
            topLeft = Offset((size.width - shadowWidth) / 2f, groundY - 2f * unit),
            size = Size(shadowWidth, 4f * unit),
        )

        translate(top = groundY - SYMBOL_BOTTOM * unit - height * HOP_HEIGHT_RATIO * size.width) {
            scale(
                scaleX = 1f + 0.16f * squash - stretch,
                scaleY = 1f - 0.2f * squash + stretch,
                pivot = Offset(size.width / 2f, SYMBOL_BOTTOM * unit),
            ) {
                scale(unit, pivot = Offset.Zero) {
                    drawPath(
                        path = body,
                        color = color,
                        style = Stroke(width = 7.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    // 착지할 때 눈을 찡그리고, 날아오를 때는 위를 본다.
                    val eyeHeight = EYE_RADIUS * 2f * blink * (1f - 0.4f * squash)
                    val eyeTop = EYE_Y - eyeHeight / 2f - 2f * height
                    for (eyeX in EyeXs) {
                        drawOval(
                            color = color,
                            topLeft = Offset(eyeX - EYE_RADIUS, eyeTop),
                            size = Size(EYE_RADIUS * 2f, eyeHeight),
                        )
                    }
                }
            }
        }
    }
}

/** 0 은 바닥에 선 상태. 앞부분에서 바닥을 누르고 나머지 동안 포물선으로 난다. */
@Composable
private fun rememberHop(bouncing: Boolean): Animatable<Float, AnimationVector1D> {
    val hop = remember { Animatable(0f) }
    LaunchedEffect(bouncing) {
        if (bouncing) {
            while (true) {
                hop.animateTo(1f, tween(HOP_MILLIS, easing = LinearEasing))
                hop.snapTo(0f)
            }
        } else if (hop.value > 0f) {
            hop.animateTo(1f, tween(((1f - hop.value) * HOP_MILLIS).toInt(), easing = LinearEasing))
            hop.snapTo(0f)
        }
    }
    return hop
}

@Composable
private fun rememberBlink(): State<Float> =
    rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                keyframes {
                    durationMillis = BLINK_PERIOD_MILLIS
                    1f at BLINK_PERIOD_MILLIS - 200
                    0.1f at BLINK_PERIOD_MILLIS - 120
                    1f at BLINK_PERIOD_MILLIS - 40
                },
                RepeatMode.Restart,
            ),
        label = "blink",
    )

private const val SYMBOL_PATH =
    "M30,44H24A12,12 0 0 1 12,32V20A12,12 0 0 1 24,8H40A12,12 0 0 1 52,20V44C52,51 50,56 44,56H16"
private const val VIEWPORT = 64f

/** 심벌 선의 아래 끝(56 + 선 두께의 절반). 이 선을 바닥으로 삼아 찌그러뜨린다. */
private const val SYMBOL_BOTTOM = 59.8f
private const val EYE_Y = 24f
private const val EYE_RADIUS = 4f
private val EyeXs = floatArrayOf(25f, 39f)

private const val HOP_MILLIS = 800
private const val SQUASH_FRACTION = 0.22f
private const val HOP_HEIGHT_RATIO = 0.45f
private const val STRETCH = 0.06f
private const val BLINK_PERIOD_MILLIS = 3200
