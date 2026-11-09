package app.manyak.studio.presentation.component

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
import app.manyak.studio.R as StudioR

/**
 * 간편 제작 첫 진입 안내의 마지막 단계. 화면이 보이는 동안만 안내를 몰고, "오리지널 스토리 보기"는 하단 탭처럼 홈
 * 탭으로 바꾼다.
 */
@Composable
internal fun StudioTutorialDialog(
    tutorial: CreateTutorialController,
    onOpenHome: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(tutorial, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { tutorial.drive() }
    }
    val stage by tutorial.stage.collectAsStateWithLifecycle()
    if (stage != CreateTutorialStage.COMPLETING) return
    CreateTutorialDialog(
        message = stringResource(StudioR.string.studio_tutorial_completing),
        primaryLabel = stringResource(StudioR.string.studio_tutorial_originals),
        onPrimary = {
            tutorial.primary()
            onOpenHome()
        },
        secondaryLabel = stringResource(StudioR.string.studio_tutorial_close),
        onSecondary = tutorial::secondary,
    )
}
