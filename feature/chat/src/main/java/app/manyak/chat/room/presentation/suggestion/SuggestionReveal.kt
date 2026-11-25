package app.manyak.chat.room.presentation.suggestion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 이번 묶음을 아직 보여 준 적이 없는지.
 *
 * 추천 영역은 메시지 목록의 한 항목이라 화면 밖으로 나가면 사라졌다가 돌아온다. 무엇을 보여 줬는지
 * 기억해 두지 않으면 스크롤을 오갈 때마다 추천이 다시 흘러 들어온다. 항목이 사라져도 남도록
 * saveable 로 든다.
 */
@Composable
internal fun revealsOnce(key: String?): Boolean {
    var played by rememberSaveable { mutableStateOf<String?>(null) }
    val animated = key != null && key != played
    LaunchedEffect(key) { if (key != null) played = key }
    return animated
}

/**
 * 지금 그리는 묶음을 가르는 값. 같은 턴이라도 스켈레톤 → 추천처럼 내용이 바뀌면 다시 흘러야 한다.
 *
 * 그릴 것이 없으면 null 이다 — 빈 화면을 "보여 준 것"으로 기억하면 곧 도착할 추천이 그냥 나타난다.
 */
internal fun revealKeyOf(
    suggestions: ChatSuggestions,
    progress: ChoicesProgress?,
    lastTurnId: Long?,
    choicesEnabled: Boolean,
): String? =
    when {
        suggestions.hasCandidate -> "items:${suggestions.sourceTurnId}"
        !showsChoicesProgress(progress, lastTurnId, choicesEnabled) -> null
        progress?.failed == true -> "error:${progress.turnId}"
        else -> "loading:${progress?.turnId}"
    }

/**
 * 웹과 같은 등장 — 조금 아래에서 흐릿하게 올라오고, 항목마다 한 박자씩 늦게 출발한다.
 *
 * **자리는 처음부터 차지한 채 투명도와 위치만 움직인다.** 높이까지 자라게 하면 뒤 항목이 밀려 목록이
 * 두 번 움직이고, 늦게 출발하는 항목일수록 그 밀림이 겹친다.
 */
@Composable
internal fun Modifier.revealed(
    index: Int,
    animated: Boolean,
): Modifier {
    val millis = ManyakTheme.motion.listItemEnterMillis
    val stagger = ManyakTheme.motion.listItemStaggerMillis
    val progress = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animated) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(millis, delayMillis = index * stagger, easing = LinearOutSlowInEasing),
            )
        }
    }
    val offset = with(LocalDensity.current) { RevealOffset.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offset
    }
}

/** 등장할 때 아래에서 올라오는 거리. 웹의 8px 과 같다. */
private val RevealOffset = 8.dp
