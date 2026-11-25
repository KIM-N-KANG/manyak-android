package app.manyak.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.delay

/**
 * 생성 계열 대기 문구. 문구 전체를 4초마다 바꾸고 글자가 차례로 위로 교차하며 시머가 지나간다.
 * [style] 은 놓이는 자리의 글과 맞춘다 — 읽는 본문 사이에서는 본문 서체를 따른다.
 */
@Composable
fun CyclingPhrases(
    phrases: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = ManyakTheme.typography.bodyMedium,
) {
    if (phrases.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(phrases) {
        while (true) {
            delay(PHRASE_INTERVAL_MS)
            index = (index + 1) % phrases.size
        }
    }
    val phrase = phrases[index % phrases.size]
    val brush = rememberTextShimmerBrush()
    val enterMillis = ManyakTheme.motion.elementEnterMillis
    val exitMillis = ManyakTheme.motion.elementExitMillis
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingDots()
        AnimatedContent(
            targetState = phrase,
            transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
            label = "loading-phrase",
            modifier =
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = phrase }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(brush, blendMode = BlendMode.SrcIn)
                    },
        ) { activePhrase ->
            FlowRow {
                activePhrase.forEachIndexed { characterIndex, character ->
                    val delayMillis = characterIndex * CHARACTER_STAGGER_MS
                    Text(
                        modifier =
                            Modifier.animateEnterExit(
                                enter =
                                    fadeIn(tween(enterMillis, delayMillis)) +
                                        slideInVertically(tween(enterMillis, delayMillis)) { it },
                                exit =
                                    fadeOut(tween(exitMillis, (delayMillis * 0.45f).toInt())) +
                                        slideOutVertically(tween(exitMillis, (delayMillis * 0.45f).toInt())) { -it },
                            ),
                        text = character.toString(),
                        style = style,
                        color = ManyakTheme.colors.textSubtle,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingDots() {
    val transition = rememberInfiniteTransition(label = "loading-dots")
    val dotColor = ManyakTheme.colors.textSubtle
    val dotSize = ManyakTheme.sizes.iconSmall
    Row(Modifier.size(dotSize)) {
        repeat(3) { dot ->
            val lift by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(500, easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(dot * 160),
                    ),
                label = "loading-dot-$dot",
            )
            Canvas(Modifier.weight(1f).height(dotSize)) {
                drawCircle(
                    color = dotColor,
                    radius = size.height / 10,
                    center = Offset(size.width / 2, size.height * (0.5f - lift * 0.3f)),
                    alpha = 0.5f + lift * 0.5f,
                )
            }
        }
    }
}

private const val PHRASE_INTERVAL_MS = 4_000L
private const val CHARACTER_STAGGER_MS = 25
