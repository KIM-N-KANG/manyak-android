package app.manyak.create.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.common.entity.story.CreateTutorialStage
import app.manyak.common.presentation.story.CreateTutorialController
import app.manyak.designsystem.component.CreateTutorialDialog
import app.manyak.create.R as CreateR

/**
 * 간편 제작 화면의 첫 진입 안내. 화면이 보이는 동안만 [tutorial] 을 몰아 떠나는 중이거나 백그라운드에서는 열지
 * 않고, 열린 단계의 대화창을 그린다.
 *
 * @param genres 스토리라인 단계 문구에 넣을 장르 이름. 생성 요청의 선택 순서다.
 */
@Composable
internal fun FunnelTutorial(
    tutorial: CreateTutorialController,
    genres: List<String> = emptyList(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(tutorial, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { tutorial.drive() }
    }
    val stage by tutorial.stage.collectAsStateWithLifecycle()
    // 마지막 단계는 제작 탭이 연다.
    val current = stage?.takeUnless { it == CreateTutorialStage.COMPLETING } ?: return
    // 단계마다 대화창을 따로 부르면 창이 닫혔다 다시 열리며 깜빡인다. 한 자리에서 문구와 버튼만 바꾼다.
    val intro = current == CreateTutorialStage.INTRO
    CreateTutorialDialog(
        message = funnelTutorialMessage(current, genres),
        primaryLabel =
            stringResource(
                if (intro) CreateR.string.create_tutorial_start else CreateR.string.create_tutorial_confirm,
            ),
        onPrimary = tutorial::primary,
        secondaryLabel = if (intro) stringResource(CreateR.string.create_tutorial_skip) else null,
        onSecondary = tutorial::secondary,
    )
}

/** 단계 문구. */
@Composable
private fun funnelTutorialMessage(
    stage: CreateTutorialStage,
    genres: List<String>,
): String =
    when (stage) {
        CreateTutorialStage.INTRO -> stringResource(CreateR.string.create_tutorial_intro)
        CreateTutorialStage.KEYWORD -> stringResource(CreateR.string.create_tutorial_keyword)
        CreateTutorialStage.STORYLINE -> {
            val fallback = stringResource(CreateR.string.create_tutorial_storyline_genre_fallback)
            val genre = genres.joinToString(", ").ifEmpty { fallback }
            stringResource(CreateR.string.create_tutorial_storyline, genre + euroParticle(genre))
        }
        CreateTutorialStage.ADDITIONAL_INFO -> stringResource(CreateR.string.create_tutorial_additional_info)
        CreateTutorialStage.COMPLETING -> ""
    }

/** 낱말 뒤의 "으로"와 "로". 마지막 글자에 받침이 없거나 ㄹ 받침이면, 한글이 아니면 로다. */
internal fun euroParticle(word: String): String {
    val last = word.lastOrNull() ?: return "로"
    if (last !in '가'..'힣') return "로"
    val finalConsonant = (last - '가') % FINAL_CONSONANT_COUNT
    return if (finalConsonant == 0 || finalConsonant == FINAL_CONSONANT_RIEUL) "로" else "으로"
}

private const val FINAL_CONSONANT_COUNT = 28
private const val FINAL_CONSONANT_RIEUL = 8
