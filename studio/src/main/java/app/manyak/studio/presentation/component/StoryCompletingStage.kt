// 소품의 자리와 크기는 표지 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.manyak.designsystem.component.drawManyakMascot
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * 스토리 완성 중 표지. 빈 표지 바탕에 옅은 점을 깔고, 로고 마스코트가 키워드를 고르고 · 스토리라인을 고르고 ·
 * 원고를 쓰고 · 그림을 그린 뒤 뛰어다니는 막을, 사이사이 쉬어 가며 이어서 연기한다. 안무는
 * [completingMoment] 가 정하고 여기서는 그리기만 한다.
 *
 * 크기는 받은 자리에 맞추고 모든 소품을 폭에 대한 비율로 그려, 표지 폭이 바뀌어도 장면이 같은 구도로 남는다.
 */
@Composable
internal fun StoryCompletingStage(
    label: String,
    modifier: Modifier = Modifier,
) {
    val time by rememberInfiniteTransition(label = "story-completing").animateFloat(
        initialValue = 0f,
        targetValue = CompletingLoopMillis.toFloat(),
        animationSpec = infiniteRepeatable(tween(CompletingLoopMillis, easing = LinearEasing)),
        label = "clock",
    )
    val colors = ManyakTheme.colors
    val palette =
        StagePalette(
            brand = colors.brand,
            paper = colors.surfaceRaised,
            ink = colors.borderStrong,
            key = colors.border,
            dot = colors.borderStrong.copy(alpha = 0.45f),
        )
    val dotGap = ManyakTheme.sizes.generationDotGap
    val dotRadius = ManyakTheme.sizes.generationDotRadius

    Canvas(
        modifier =
            modifier.semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        val millis = time.toInt()
        val (act, actMillis, pose) = completingMoment(millis)
        val unit = size.width
        drawDots(palette.dot, dotGap, dotRadius)
        when (act) {
            CompletingAct.KEYWORDS -> drawKeywordChips(actMillis, unit, palette)
            CompletingAct.STORYLINE -> drawStorylines(actMillis, unit, palette)
            CompletingAct.TYPING -> drawManuscript(actMillis, unit, palette)
            CompletingAct.PAINTING -> drawPainting(actMillis, unit, palette)
            CompletingAct.BOUNCING, CompletingAct.WALL_JUMP, CompletingAct.INTERLUDE -> Unit
        }
        // 칩을 밟고 있을 때는 바닥 그림자가 허공에 뜬 것처럼 보여 두지 않는다.
        if (act != CompletingAct.KEYWORDS) drawShadow(pose, unit, palette.brand)
        drawManyakMascot(
            feet = Offset(pose.x, pose.y) * unit,
            size = MASCOT_SIZE * unit,
            color = palette.brand,
            scaleX = pose.scaleX,
            scaleY = pose.scaleY,
            rotationDegrees = pose.rotation,
            look = pose.look,
            eyeOpenness = blinkOpenness(millis) * (1f - 0.9f * pose.squint),
        )
        if (act == CompletingAct.TYPING) drawKeyboard(actMillis, unit, palette)
    }
}

internal class StagePalette(
    val brand: Color,
    val paper: Color,
    val ink: Color,
    val key: Color,
    val dot: Color,
)

/**
 * 소품이 막 시작에 살짝 넘치며 나타나고 막 끝에 사라지는 정도. 1 을 넘는 값은 등장 때의 넘침이다.
 * [enterDelay] 만큼 늦게 나타나 여러 소품이 차례로 튀어나온다.
 */
internal fun propVisibility(
    act: CompletingAct,
    actMillis: Int,
    enterDelay: Int = 0,
): Float {
    val enter = easeOutBack(((actMillis - enterDelay) / 320f).coerceIn(0f, 1f))
    val exit = ((actMillis - (completingActMillis(act) - 320)) / 320f).coerceIn(0f, 1f)
    return enter * (1f - exit)
}

/**
 * 소품 묶음을 한 장으로 그린 뒤 투명도를 입힌다. 겹치는 붓질·둥근 선 끝을 각각 반투명하게 그리면 겹친 자리만
 * 진해지므로, 소품은 불투명하게 그리고 투명도는 여기서 한 번에 준다.
 */
internal inline fun DrawScope.withAlpha(
    alpha: Float,
    block: DrawScope.() -> Unit,
) {
    if (alpha >= 1f) {
        block()
        return
    }
    if (alpha <= 0f) return
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

/** 원고·키보드·카드·액자가 함께 쓰는 종이. 다크 테마에서도 바탕과 갈리도록 진한 경계로 두른다. */
internal fun DrawScope.drawCard(
    rect: Rect,
    unit: Float,
    palette: StagePalette,
    outline: Color = palette.ink,
    fill: Color = palette.paper,
    corner: Float = 0.04f,
) {
    val topLeft = rect.topLeft * unit
    val cardSize = rect.size * unit
    val radius = CornerRadius(corner * unit)
    drawRoundRect(fill, topLeft, cardSize, radius)
    drawRoundRect(outline, topLeft, cardSize, radius, style = Stroke(width = 0.008f * unit))
}

/** 이미지 생성 로딩과 같은 간격의 점을 움직이지 않게 깐다. 장면이 바쁘므로 바탕은 가만히 둔다. */
private fun DrawScope.drawDots(
    color: Color,
    gap: Dp,
    radius: Dp,
) {
    val gapPx = gap.toPx()
    val columns = (size.width / gapPx).toInt()
    val rows = (size.height / gapPx).toInt()
    val left = (size.width - columns * gapPx) / 2f
    val top = (size.height - rows * gapPx) / 2f
    for (row in 0..rows) {
        for (column in 0..columns) {
            drawCircle(color, radius.toPx(), Offset(left + column * gapPx, top + row * gapPx))
        }
    }
}

/** 바닥에 깔리는 그림자. 높이 뜰수록 작고 옅어진다. */
private fun DrawScope.drawShadow(
    pose: MascotPose,
    unit: Float,
    color: Color,
) {
    val height = ((FLOOR - pose.y) / 0.9f).coerceIn(0f, 1f)
    val width = MASCOT_SIZE * 0.62f * (1f - 0.5f * height) * pose.scaleX * unit
    drawOval(
        color = color.copy(alpha = 0.16f * (1f - 0.7f * height)),
        topLeft = Offset(pose.x * unit - width / 2f, (FLOOR - 0.012f) * unit),
        size = Size(width, 0.024f * unit),
    )
}

/** 2.9초마다 한 번 깜빡인다. */
private fun blinkOpenness(millis: Int): Float {
    val phase = millis % 2900
    return if (phase < 2760) 1f else 1f - 0.9f * sin(PI.toFloat() * (phase - 2760) / 140f)
}

/** 살짝 넘쳤다가 자리를 잡는 등장 곡선. */
internal fun easeOutBack(t: Float): Float = 1f + 2.70158f * (t - 1f).pow(3) + 1.70158f * (t - 1f).pow(2)
