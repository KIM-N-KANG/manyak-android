// 파일이 담는 것은 로딩 컴포저블 셋이고 GenerationHint 는 그 파라미터 타입일 뿐이다.
@file:Suppress("MatchingDeclarationName")

package app.manyak.feature.create

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import kotlinx.coroutines.delay

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
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
        ) {
            TypewriterPhrases(phrases = stringArrayResource(phrasesRes).toList())
            GenerationHints(hints = hints)
        }
    }
}

/** 문구를 한 글자씩 드러내고, 다 쓰면 잠시 머문 뒤 다음 문구로 순환한다. */
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
            index = (index + 1) % phrases.size
            text = ""
        }
    }

    // 빈 순간에도 줄 높이를 유지해 아래 힌트가 흔들리지 않게 한다.
    Text(
        modifier = modifier,
        text = text.ifEmpty { " " },
        style = ManyakTheme.typography.bodyLarge,
        color = ManyakTheme.colors.text,
    )
}

/** 생성이 길어지면 각 힌트 시점에 하나씩 쌓아 보여 준다. */
@Composable
private fun GenerationHints(
    hints: List<GenerationHint>,
    modifier: Modifier = Modifier,
) {
    var revealedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(hints) {
        var elapsedMs = 0L
        hints.forEachIndexed { index, hint ->
            delay(hint.delayMs - elapsedMs)
            elapsedMs = hint.delayMs
            revealedCount = index + 1
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        hints.take(revealedCount).forEach { hint ->
            Text(
                text = stringResource(hint.textRes),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.textSubtle,
            )
        }
    }
}

private const val TYPEWRITER_CHAR_DELAY_MS = 60L
private const val TYPEWRITER_PHRASE_HOLD_MS = 1_400L

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
