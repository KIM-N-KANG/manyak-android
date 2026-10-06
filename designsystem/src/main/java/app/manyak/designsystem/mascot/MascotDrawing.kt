// 소품의 모양은 심벌 viewport 나 그림 격자 안에서 한 번씩만 쓰는 연출 수치다. 무대가 함께 쓰는 그리기
// 도구를 한 파일에 모아 둔다.
@file:Suppress("MagicNumber", "TooManyFunctions", "MatchingDeclarationName")

package app.manyak.designsystem.mascot

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import app.manyak.designsystem.component.MASCOT_VIEWPORT
import app.manyak.designsystem.theme.ManyakTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** 마스코트 무대가 쓰는 테마 색. [pencil] 은 연필 선처럼 종이 위에서 또렷해야 하는 회색이다. */
class StagePalette(
    val brand: Color,
    val paper: Color,
    val ink: Color,
    val key: Color,
    val pencil: Color,
)

@Composable
fun stagePalette(): StagePalette {
    val colors = ManyakTheme.colors
    return StagePalette(
        brand = colors.brand,
        paper = colors.surfaceRaised,
        ink = colors.borderStrong,
        key = colors.border,
        pencil = colors.textSubtlest,
    )
}

/**
 * 무대가 처음 그려진 뒤 흐른 시간. 기기에서 애니메이션을 끄면 [stillMillis] 장면에 멈춘다. 상태를 그리기
 * 단계에서만 읽으면 프레임마다 다시 그리기만 하고 재구성하지 않는다.
 */
@Composable
fun rememberStageMillis(stillMillis: Int): State<Int> {
    val resolver = LocalContext.current.contentResolver
    val still = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val millis = remember { mutableIntStateOf(if (still) stillMillis else 0) }
    if (!still) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { millis.intValue = (it - start).toInt() }
        }
    }
    return millis
}

/** 두 색을 sRGB 값 그대로 [t] 만큼 섞는다. 웹 무대와 같은 색이 나오도록 지각 색 공간을 거치지 않는다. */
fun mix(
    a: Color,
    b: Color,
    t: Float,
) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** 무대 바탕에 옅은 점을 고르게 깐다. 장면이 바쁘므로 바탕은 가만히 둔다. */
fun DrawScope.drawStageDots(
    color: Color,
    gap: Float,
    radius: Float,
) {
    val columns = (size.width / gap).toInt()
    val rows = (size.height / gap).toInt()
    val left = (size.width - columns * gap) / 2f
    val top = (size.height - rows * gap) / 2f
    for (row in 0..rows) {
        for (column in 0..columns) drawCircle(color, radius, Offset(left + column * gap, top + row * gap))
    }
}

/** 바닥에 깔리는 그림자. 높이 뜰수록 작고 옅어진다. */
fun DrawScope.drawStageShadow(
    pose: MascotPose,
    unit: Float,
    mascotSize: Float,
    floor: Float,
    color: Color,
) {
    val height = ((floor - pose.y) / 0.9f).coerceIn(0f, 1f)
    val width = mascotSize * 0.62f * (1f - 0.5f * height) * pose.scaleX * unit
    val depth = 0.1f * mascotSize * unit
    drawOval(
        color = color.copy(alpha = color.alpha * 0.16f * (1f - 0.7f * height)),
        topLeft = Offset(pose.x * unit - width / 2f, floor * unit - depth / 2f),
        size = Size(width, depth),
    )
}

/**
 * 소품 묶음을 한 장으로 그린 뒤 투명도를 입힌다. 겹치는 붓질·둥근 선 끝을 각각 반투명하게 그리면 겹친 자리만
 * 진해지므로, 소품은 불투명하게 그리고 투명도는 여기서 한 번에 준다. 무대 밖으로 걸친 소품도 잘리지 않게
 * 무대보다 넓게 잡는다.
 */
inline fun DrawScope.withAlpha(
    alpha: Float,
    block: DrawScope.() -> Unit,
) {
    if (alpha <= 0f) return
    if (alpha >= 1f) {
        block()
        return
    }
    val bounds = Rect(-size.width, -size.height, size.width * 2f, size.height * 2f)
    drawContext.canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
    block()
    drawContext.canvas.restore()
}

/** 끝이 둥근 직선. 자리와 굵기는 무대 폭 단위다. */
fun DrawScope.stageLine(
    from: Offset,
    to: Offset,
    width: Float,
    color: Color,
    unit: Float,
) = drawLine(color, from * unit, to * unit, strokeWidth = width * unit, cap = StrokeCap.Round)

/** 마스코트 몸의 한 점(심벌 viewport 좌표)이 무대 어디에 있는지. */
fun MascotPose.onBody(
    at: Offset,
    mascotSize: Float,
): Offset = Offset(x, y) + mascotPointOffset(at, rotation, scaleX, scaleY, mascotSize)

/**
 * 무대의 [at] 자리로 옮겨 [angle] 만큼 돌리고 심벌 viewport 단위로 늘린 뒤 그린다. 마스코트가 든 소품을 몸과
 * 같은 크기로 그릴 때 쓴다.
 */
fun DrawScope.inViewport(
    unit: Float,
    mascotSize: Float,
    at: Offset,
    angle: Float,
    scaleX: Float,
    block: DrawScope.() -> Unit,
) {
    val cell = mascotSize * unit / MASCOT_VIEWPORT
    translate(at.x * unit, at.y * unit) {
        rotate(angle, pivot = Offset.Zero) {
            scale(cell * scaleX, cell, pivot = Offset.Zero) { block() }
        }
    }
}

/**
 * 살짝 한쪽으로 처진 화가 베레모. 꼭지와 띠를 달고, 심벌 viewport 단위로 얹힐 자리를 원점으로 그린다. 브랜드
 * 색을 바탕 밝기의 반대쪽으로 섞어 테마마다 바탕과 갈리게 한다.
 *
 * @param muted 무대에서 튀지 않게 연필 회색으로 칠하려면 true
 */
fun DrawScope.drawBeret(
    palette: StagePalette,
    muted: Boolean = false,
) {
    val paper = palette.paper
    val shade = if (paper.red + paper.green + paper.blue > 1.5f) Color.Black else Color.White
    val felt = if (muted) palette.pencil else mix(palette.brand, shade, 0.45f)
    drawPath(
        Path().apply {
            moveTo(-17f, 1.5f)
            cubicTo(-19.5f, -6f, -8f, -10.5f, 2f, -10.5f)
            cubicTo(12f, -10.5f, 20.5f, -6f, 17.5f, 0.5f)
            cubicTo(15f, 3.6f, -13f, 4.6f, -17f, 1.5f)
            close()
        },
        felt,
    )
    drawPath(
        Path().apply {
            moveTo(-15.5f, 2f)
            quadraticTo(0f, 5f, 16f, 1f)
        },
        if (muted) mix(felt, shade, 0.35f) else mix(palette.brand, shade, 0.65f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )
    drawLine(felt, Offset(1f, -10f), Offset(2.4f, -14f), strokeWidth = 2.8f, cap = StrokeCap.Round)
}

/**
 * 붓. 쥔 자리를 원점으로, 붓털 끝이 아래(+y)로 가게 심벌 viewport 단위로 그린다. 위로 자루, 쥔 자리 아래로
 * 쇠테, 그 아래로 물감 묻은 붓털이 뾰족하게 모인다.
 */
fun DrawScope.drawBrush(
    palette: StagePalette,
    paint: Color,
) {
    drawLine(palette.pencil, Offset(0f, -BRUSH_HANDLE_LENGTH), Offset(0f, -2f), 4f, StrokeCap.Round)
    drawLine(palette.ink, Offset(0f, -2f), Offset(0f, 4f), 5.2f, StrokeCap.Round)
    val bristles =
        Path().apply {
            moveTo(-2.8f, 4f)
            cubicTo(-4.4f, 8.5f, -2f, 12.5f, 0f, BRUSH_TIP_LENGTH)
            cubicTo(2f, 12.5f, 4.4f, 8.5f, 2.8f, 4f)
            close()
        }
    drawPath(bristles, paint)
    drawPath(bristles, palette.pencil, style = Stroke(width = 0.8f))
}

/**
 * 붓길을 [progress] 만큼 긋는다. 마디마다 굵기를 바꿔 붓이 눌렸다 들리는 결을 내고, [offset] 이 있으면 결을
 * 따라 옆으로 비껴 긋는다. 격자 좌표로 옮긴 캔버스에 그린다.
 *
 * @param pressure 길이 비율 → 굵기 비율
 * @param offset 누적 길이(격자 단위) → 옆으로 비낄 거리
 */
fun DrawScope.drawStroke(
    stroke: BrushPath,
    progress: Float,
    width: Float,
    color: Color,
    pressure: (Float) -> Float,
    offset: ((Float) -> Float)? = null,
) {
    if (progress <= 0f) return
    val points = stroke.points
    val lengths = stroke.lengths
    val end = progress.coerceAtMost(1f) * stroke.total

    fun shifted(index: Int): Offset {
        if (offset == null) return points[index]
        val along = points[minOf(index + 1, points.lastIndex)] - points[maxOf(index - 1, 0)]
        val length = along.getDistance().takeIf { it > 0f } ?: 1f
        val away = offset(lengths[index])
        return points[index] + Offset(-along.y, along.x) * (away / length)
    }

    for (i in 1 until points.size) {
        if (lengths[i - 1] >= end) break
        val span = lengths[i] - lengths[i - 1]
        val t = if (span > 0f) ((end - lengths[i - 1]) / span).coerceAtMost(1f) else 1f
        val from = shifted(i - 1)
        drawLine(
            color = color,
            start = from,
            end = from + (shifted(i) - from) * t,
            strokeWidth = width * pressure((lengths[i - 1] + span * t * 0.5f) / stroke.total),
            cap = StrokeCap.Round,
        )
    }
}

/** 연필 결. 양 끝만 살짝 가늘고 중간은 고르되 손떨림처럼 조금씩 굵기가 바뀐다. */
fun pencilPressure(s: Float): Float =
    (0.55f + 0.45f * sin(PI.toFloat() * s).coerceAtLeast(0f).pow(0.3f)) * (0.85f + 0.15f * sin(s * 37f))

/** 붓 결. 양 끝은 가늘고 가운데가 눌려 굵다. */
fun brushPressure(s: Float): Float = 0.2f + 0.8f * sin(PI.toFloat() * s).coerceAtLeast(0f).pow(0.6f)

/** 가장자리가 물에 번진 듯 울퉁불퉁한 둥근 얼룩. 격자 좌표로 옮긴 캔버스에 칠한다. */
fun DrawScope.drawWobblyBlob(
    center: Offset,
    radius: Float,
    color: Color,
    seed: Float,
) {
    val path = Path()
    for (step in 0..32) {
        val angle = step / 32f * 2f * PI.toFloat()
        val wobble = 1f + 0.05f * sin(5f * angle + seed) + 0.04f * sin(3f * angle + 1f)
        val x = center.x + cos(angle) * radius * wobble
        val y = center.y + sin(angle) * radius * wobble
        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
