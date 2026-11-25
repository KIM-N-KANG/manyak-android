package app.manyak.create.general.presentation.image

import kotlin.math.min

internal fun cropOutputSize(
    width: Int,
    height: Int,
    aspectRatio: Float,
): Pair<Int, Int> {
    val horizontal = if (aspectRatio < 1f) SHORT_UNIT else LONG_UNIT
    val vertical = if (aspectRatio < 1f) LONG_UNIT else SHORT_UNIT
    val units = min(width / horizontal, height / vertical).coerceIn(1, MAX_OUTPUT_SIDE / LONG_UNIT)
    return horizontal * units to vertical * units
}

private const val SHORT_UNIT = 3
private const val LONG_UNIT = 4
private const val MAX_OUTPUT_SIDE = 1440
