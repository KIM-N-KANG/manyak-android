package app.manyak.chat.room.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import app.manyak.designsystem.component.rememberTextShimmerBrush
import app.manyak.designsystem.theme.ManyakTheme

/** 기존 채팅 대기 문구의 색과 속도를 유지한다. */
@Composable
internal fun rememberWritingShimmerBrush(): Brush {
    val base = ManyakTheme.colors.textSubtlest
    return rememberTextShimmerBrush(
        base = base,
        highlight = if (isSystemInDarkTheme()) ManyakTheme.colors.text else base.copy(alpha = 0.2f),
        durationMillis = 2_000,
    )
}
