package app.manyak.story.detail.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.story.detail.presentation.component.META_KEY

@Composable
internal fun storyFooterBackground(listState: LazyListState): Color {
    val progress by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val metadata = layout.visibleItemsInfo.firstOrNull { it.key == META_KEY }
            if (metadata == null) {
                0f
            } else {
                val remaining =
                    (metadata.offset + metadata.size + layout.afterContentPadding - layout.viewportEndOffset)
                        .coerceAtLeast(0)
                // 메타 정보가 처음 들어오는 순간을 0으로 맞춰 시작 지점의 색상 점프를 없앤다.
                val distance = (metadata.size + layout.afterContentPadding).coerceAtLeast(1).toFloat()
                val fraction = if (listState.canScrollForward) (1f - remaining / distance).coerceIn(0f, 1f) else 1f
                fraction * fraction * (3f - 2f * fraction)
            }
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = ManyakTheme.motion.elementEnterMillis, easing = LinearEasing),
        label = "storyFooterBackground",
    )
    return lerp(ManyakTheme.colors.surface, ManyakTheme.colors.backgroundNeutral, animatedProgress)
}
