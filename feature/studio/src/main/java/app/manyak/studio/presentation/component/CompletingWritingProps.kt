// 소품의 자리와 크기는 표지 폭에 대한 비율로 한 번씩만 쓰는 연출 수치다.
@file:Suppress("MagicNumber")

package app.manyak.studio.presentation.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.manyak.designsystem.mascot.StagePalette
import app.manyak.designsystem.mascot.easeOutBack
import app.manyak.designsystem.mascot.mix
import app.manyak.designsystem.mascot.withAlpha

// 원고는 키보드 위에 떠서 한 줄씩 차오르고, 키보드는 마스코트 앞을 가린다.
private val Paper = Rect(0.2f, 0.24f, 0.8f, 0.84f)
private val Keyboard = Rect(0.16f, FLOOR - 0.085f, 0.84f, FLOOR + 0.075f)
private val LineWidths = floatArrayOf(0.62f, 1f, 0.9f, 1f, 0.55f)
private const val KEY_INSET = 0.024f
private const val KEY_GAP = 0.012f

/** 원고와 키보드가 함께 나타났다가, 다 쓰고 기뻐 뛰는 동안 함께 사라진다. */
private fun writingVisibility(actMillis: Int): Float {
    val enter = easeOutBack((actMillis / 320f).coerceIn(0f, 1f))
    val exit = ((actMillis - cueStart(Cue.TYPING_DONE) - 140) / 360f).coerceIn(0f, 1f)
    return enter * (1f - exit)
}

internal fun DrawScope.drawManuscript(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val visibility = writingVisibility(actMillis)
    withAlpha(visibility.coerceIn(0f, 1f)) {
        scale(0.8f + 0.2f * visibility, pivot = Offset(Paper.center.x, Paper.bottom) * unit) {
            drawCard(Paper, unit, palette)
            val (stroke, progress) = keystrokeAt(actMillis)
            val typingStart = cueStart(Cue.TYPING)
            val typed =
                if (actMillis < typingStart) {
                    0f
                } else {
                    ((stroke + ((progress - 0.5f) * 2f).coerceIn(0f, 1f)) / KEYSTROKES).coerceAtMost(1f)
                }
            val typing = actMillis in typingStart until cueStart(Cue.TYPING_DONE)
            var remaining = typed * LineWidths.sum()
            val innerLeft = Paper.left + 0.07f
            val innerWidth = Paper.width - 0.14f
            LineWidths.forEachIndexed { index, width ->
                val shown = remaining.coerceIn(0f, width)
                val writingHere = typing && remaining >= 0f && remaining < width
                remaining -= width
                val title = index == 0
                val y = Paper.top + 0.1f + index * 0.1f + if (title) 0f else 0.03f
                if (shown > 0f) {
                    drawLine(
                        color = if (title) mix(palette.paper, palette.brand, 0.75f) else palette.ink,
                        start = Offset(innerLeft, y) * unit,
                        end = Offset(innerLeft + innerWidth * shown, y) * unit,
                        strokeWidth = (if (title) 0.034f else 0.022f) * unit,
                        cap = StrokeCap.Round,
                    )
                }
                if (writingHere && (actMillis / 260) % 2 == 0) {
                    val x = innerLeft + innerWidth * shown + 0.03f
                    drawLine(
                        color = palette.brand,
                        start = Offset(x, y - 0.03f) * unit,
                        end = Offset(x, y + 0.03f) * unit,
                        strokeWidth = 0.012f * unit,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** 마스코트 앞을 가리는 키보드. 아래에서 밀려 올라오고, 방금 누른 키가 브랜드 색으로 빛났다 꺼진다. */
internal fun DrawScope.drawKeyboard(
    actMillis: Int,
    unit: Float,
    palette: StagePalette,
) {
    val visibility = writingVisibility(actMillis)
    withAlpha(visibility.coerceIn(0f, 1f)) {
        translate(top = (1f - visibility) * 0.25f * unit) {
            drawCard(Keyboard, unit, palette)
            val (stroke, progress) = keystrokeAt(actMillis)
            val typing = actMillis in cueStart(Cue.TYPING) until cueStart(Cue.TYPING_DONE)
            val keyWidth = (Keyboard.width - 2 * KEY_INSET - (KEY_COLUMNS - 1) * KEY_GAP) / KEY_COLUMNS
            val keyHeight = (Keyboard.height - 2 * KEY_INSET - 2 * KEY_GAP) / 3f
            for (key in 0 until KEY_COUNT) {
                val glow =
                    when {
                        !typing -> 0f
                        key == keyFor(stroke) -> ((progress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        stroke > 0 && key == keyFor(stroke - 1) -> 1f - (progress / 0.4f).coerceIn(0f, 1f)
                        else -> 0f
                    }
                val isSpace = key == KEY_COUNT - 1
                val column = if (isSpace) 1.5f else (key % KEY_COLUMNS).toFloat()
                val row = if (isSpace) 2 else key / KEY_COLUMNS
                val width = if (isSpace) keyWidth * 4 + KEY_GAP * 3 else keyWidth
                val left = Keyboard.left + KEY_INSET + column * (keyWidth + KEY_GAP)
                val top = Keyboard.top + KEY_INSET + row * (keyHeight + KEY_GAP) + glow * 0.006f
                drawRoundRect(
                    color = mix(palette.key, palette.brand, glow),
                    topLeft = Offset(left, top) * unit,
                    size = Size(width, keyHeight) * unit,
                    cornerRadius = CornerRadius(0.012f * unit),
                )
            }
        }
    }
}
