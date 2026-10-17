package app.manyak.create.presentation.component

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.create.presentation.state.DraftSaveMessage
import app.manyak.create.presentation.state.DraftSaveUiState
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.flow.Flow
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 진입 판정을 기다리는 헤더 아래 자리. 채팅방 진입처럼 늦어질 때만 가운데 스피너를 보이고, 서버 초안을 읽지 못하면
 * 안내와 다시 시도를 둔다.
 */
@Composable
internal fun FunnelEntryStatus(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showProgress = rememberDelayedProgressVisibility(!failed)
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(CreateR.string.create_draft_load_failed),
                style = ManyakTheme.typography.bodyMedium,
                color = ManyakTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            ManyakNeutralButton(label = stringResource(DesignsystemR.string.common_retry), onClick = onRetry)
        } else if (showProgress) {
            ManyakProgressIndicator()
        }
    }
}

/** 스토어가 저장한 단계 화면의 서버 저장 안내와 충돌 확인. 보이는 화면만 안내를 받는다. */
@Composable
internal fun DraftSaveFeedback(
    draftSave: DraftSaveUiState,
    messages: Flow<DraftSaveMessage>,
    onResolveConflict: (overwrite: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(messages, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            messages.collect { Toast.makeText(context, it.resource(), Toast.LENGTH_SHORT).show() }
        }
    }
    if (draftSave.conflictVersion != null) DraftConflictDialog(onResolveConflict)
}

/** 덮어쓰면 다른 곳에서 저장한 내용을 잃어 파괴적 확인으로 묻는다. 바깥 누르기와 뒤로가기는 서버 내용을 그대로 둔다. */
@Composable
internal fun DraftConflictDialog(onResolve: (overwrite: Boolean) -> Unit) {
    ManyakDestructiveDialog(
        title = stringResource(CreateR.string.general_editor_draft_conflict_title),
        description = stringResource(CreateR.string.general_editor_draft_conflict_description),
        confirmLabel = stringResource(CreateR.string.general_editor_draft_conflict_save),
        cancelLabel = stringResource(CreateR.string.general_editor_exit_close),
        onConfirm = { onResolve(true) },
        onDismiss = { onResolve(false) },
    )
}

@StringRes
internal fun DraftSaveMessage.resource(): Int =
    when (this) {
        DraftSaveMessage.SAVED -> CreateR.string.create_draft_saved
        DraftSaveMessage.LIMIT -> CreateR.string.general_editor_draft_limit
        DraftSaveMessage.TOO_LARGE -> CreateR.string.general_editor_draft_too_large
        DraftSaveMessage.FAILED -> CreateR.string.general_editor_draft_failed
    }
