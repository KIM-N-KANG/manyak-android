// 파일이 담는 것은 로딩 컴포저블 셋이고 GenerationHint 는 그 파라미터 타입일 뿐이다.
@file:Suppress("MatchingDeclarationName")

package app.manyak.feature.create

import android.os.SystemClock
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 생성이 길어지면 [delayMs] 시점에 드러내는 힌트. */
internal data class GenerationHint(
    val delayMs: Long,
    @param:StringRes val textRes: Int,
)

/** 스토리라인 생성 중 로딩 화면 — 로딩 제목, 타자기형 문구, 지연 힌트. */
@Composable
internal fun StorylineGeneratingContent(modifier: Modifier = Modifier) {
    GeneratingLoadingContent(
        modifier = modifier,
        titleRes = R.string.create_storyline_loading_title,
        descriptionRes = R.string.create_storyline_loading_description,
        loadingLabelRes = R.string.create_storyline_loading_label,
        phrasesRes = R.array.create_storyline_loading_phrases,
        hints =
            listOf(
                GenerationHint(delayMs = 15_000, textRes = R.string.create_storyline_loading_hint_delayed),
                GenerationHint(delayMs = 30_000, textRes = R.string.create_storyline_loading_hint_long),
            ),
    )
}

/** 스토리 완성 중 로딩 화면. 완성은 별도 목적지가 아니라 추가 정보 화면의 종료 상태다. */
@Composable
internal fun StoryCompletingContent(modifier: Modifier = Modifier) {
    GeneratingLoadingContent(
        modifier = modifier,
        titleRes = R.string.create_completion_loading_title,
        descriptionRes = R.string.create_completion_loading_description,
        loadingLabelRes = R.string.create_completion_loading_label,
        phrasesRes = R.array.create_completion_loading_phrases,
        hints =
            listOf(
                GenerationHint(delayMs = 15_000, textRes = R.string.create_completion_loading_hint_delayed),
                GenerationHint(delayMs = 30_000, textRes = R.string.create_completion_loading_hint_long),
                GenerationHint(delayMs = 60_000, textRes = R.string.create_completion_loading_hint_almost),
            ),
    )
}

/** 생성 계열 로딩 화면의 공용 골격. */
@Composable
private fun GeneratingLoadingContent(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes loadingLabelRes: Int,
    @ArrayRes phrasesRes: Int,
    hints: List<GenerationHint>,
    modifier: Modifier = Modifier,
) {
    val loadingLabel = stringResource(loadingLabelRes)
    Column(
        modifier = modifier.semantics { contentDescription = loadingLabel },
    ) {
        Column(
            modifier = Modifier.padding(ManyakTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        ) {
            Text(
                text = stringResource(titleRes),
                style = ManyakTheme.typography.titleLarge,
                color = ManyakTheme.colors.text,
            )
            Text(
                text = stringResource(descriptionRes),
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        Column(
            modifier =
                Modifier
                    .padding(horizontal = ManyakTheme.spacing.gutter)
                    .padding(top = ManyakTheme.spacing.block),
        ) {
            TypewriterPhrases(phrases = stringArrayResource(phrasesRes).toList())
            GenerationHints(hints = hints)
        }
    }
}

/** 문구를 한 글자씩 쓰고, 잠시 머문 뒤 한 글자씩 지우고 다음 문구로 순환한다. */
@Composable
private fun TypewriterPhrases(
    phrases: List<String>,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(phrases) {
        if (phrases.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            val phrase = phrases[index]
            for (length in 1..phrase.length) {
                text = phrase.take(length)
                delay(TYPEWRITER_CHAR_DELAY_MS)
            }
            delay(TYPEWRITER_PHRASE_HOLD_MS)
            for (length in phrase.length - 1 downTo 0) {
                text = phrase.take(length)
                delay(TYPEWRITER_DELETE_DELAY_MS)
            }
            index = (index + 1) % phrases.size
        }
    }

    // 빈 순간에도 줄 높이를 유지해 아래 힌트가 흔들리지 않게 한다.
    Text(
        modifier = modifier,
        text = text.ifEmpty { " " },
        style = ManyakTheme.typography.bodyMedium,
        color = ManyakTheme.colors.textSubtle,
    )
}

/**
 * 생성이 길어지면 각 힌트 시점에 "N초 지남" 구분선을 먼저 드러내고, 잠시 뒤 힌트 문구를 잇는다.
 *
 * 구분선과 문구를 시차를 두고 떨어뜨리는 이유는, 한 번에 나오면 안내가 아니라 오류 통보처럼 읽히기
 * 때문이다 — 시간이 먼저 보이고 설명이 따라오면 기다림이 진행 중인 일로 느껴진다.
 */
@Composable
private fun GenerationHints(
    hints: List<GenerationHint>,
    modifier: Modifier = Modifier,
) {
    // 경과를 화면이 아니라 시작 시각으로 센다. 이 효과는 컴포지션에 들어올 때마다 실행되므로,
    // 화면 안에서 세면 구성 변경 때마다 이미 나온 안내가 사라지고 처음부터 다시 기다리게 된다 —
    // 생성이 길어져 사용자가 가장 불안한 구간에서 안내만 없어진다.
    // 단조 시계를 쓰는 이유는 사용자가 기기 시간을 바꿔도 경과가 뒤틀리지 않게 하기 위해서다.
    val startedAtMillis = rememberSaveable { SystemClock.elapsedRealtime() }
    var separatorsRevealed by rememberSaveable { mutableIntStateOf(0) }
    var textsRevealed by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(hints, startedAtMillis) {
        hints.forEachIndexed { index, hint ->
            val remainingMs = hint.delayMs - (SystemClock.elapsedRealtime() - startedAtMillis)
            if (remainingMs > 0) delay(remainingMs)
            separatorsRevealed = maxOf(separatorsRevealed, index + 1)
            if (textsRevealed <= index) {
                delay(Random.nextLong(TEXT_REVEAL_MIN_OFFSET_MS, TEXT_REVEAL_MAX_OFFSET_MS + 1))
                textsRevealed = index + 1
            }
        }
    }

    Column(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        hints.forEachIndexed { index, hint ->
            GenerationHintBlock(
                hint = hint,
                showsSeparator = index < separatorsRevealed,
                showsText = index < textsRevealed,
            )
        }
    }
}

/**
 * 힌트 하나 — 경과 구분선과 문구. 간격을 요소 안 위쪽 패딩으로 갖는 이유는, 드러나기 전에는
 * 자리도 차지하지 않아야 하기 때문이다.
 */
@Composable
private fun GenerationHintBlock(
    hint: GenerationHint,
    showsSeparator: Boolean,
    showsText: Boolean,
) {
    HintReveal(visible = showsSeparator) {
        ElapsedSeparator(
            seconds = (hint.delayMs / MILLIS_PER_SECOND).toInt(),
            modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
        )
    }
    HintReveal(visible = showsText) {
        Text(
            modifier = Modifier.padding(top = ManyakTheme.spacing.gutter),
            text = stringResource(hint.textRes),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtlest,
        )
    }
}

/** 아래에서 살짝 떠오르며 나타난다. 처음부터 드러난 채 복원되면 애니메이션 없이 그대로 보인다. */
@Composable
private fun HintReveal(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val slidePx = with(LocalDensity.current) { HintSlideDistance.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tween(HINT_ENTER_MILLIS, easing = LinearOutSlowInEasing)) +
                slideInVertically(tween(HINT_ENTER_MILLIS, easing = LinearOutSlowInEasing)) { slidePx },
    ) {
        content()
    }
}

/** "N초 지남"을 가운데 두고 양쪽으로 선을 채운 구분선. */
@Composable
private fun ElapsedSeparator(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        SeparatorLine(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.create_loading_hint_elapsed, seconds),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtlest,
        )
        SeparatorLine(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SeparatorLine(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .height(SeparatorLineWidth)
                .background(ManyakTheme.colors.border),
    )
}

private const val TYPEWRITER_CHAR_DELAY_MS = 90L
private const val TYPEWRITER_DELETE_DELAY_MS = 50L
private const val TYPEWRITER_PHRASE_HOLD_MS = 1_200L
private const val TEXT_REVEAL_MIN_OFFSET_MS = 1_000L
private const val TEXT_REVEAL_MAX_OFFSET_MS = 2_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val HINT_ENTER_MILLIS = 500

private val HintSlideDistance = 8.dp
private val SeparatorLineWidth = 1.dp

@Preview(showBackground = true, name = "스토리라인 선택 · 생성 중")
@Composable
private fun StorylineGeneratingContentPreview() {
    ManyakTheme(darkTheme = false) {
        StorylineGeneratingContent()
    }
}

@Preview(showBackground = true, name = "추가 정보 · 완성 중")
@Composable
private fun StoryCompletingContentPreview() {
    ManyakTheme(darkTheme = false) {
        StoryCompletingContent()
    }
}
