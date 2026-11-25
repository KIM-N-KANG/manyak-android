package app.manyak.notification.inbox.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import app.manyak.common.presentation.error.messageResOrNull
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakNeutralButton
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.notification.R as NotificationR

@Composable
fun NotificationInboxScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    LaunchedEffect(viewModel, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is NotificationInboxEffect.Navigate -> onNavigate(effect.destination)
                    NotificationInboxEffect.ReadAllFailed ->
                        Toast
                            .makeText(
                                context,
                                NotificationR.string.inbox_read_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                    is NotificationInboxEffect.RefreshFailed ->
                        Toast
                            .makeText(
                                context,
                                effect.error.messageResOrNull() ?: NotificationR.string.inbox_load_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                }
            }
        }
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        InboxHeader(state, onBack, onOpenSettings) { viewModel.onIntent(NotificationInboxIntent.ReadAll) }
        NotificationInboxContent(state, viewModel::onIntent, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxHeader(
    state: NotificationInboxUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onReadAll: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(NotificationR.string.inbox_title),
                style = ManyakTheme.typography.bodyLargeStrong,
            )
        },
        navigationIcon = {
            ManyakIconButton(DesignsystemR.drawable.ic_arrow_back, stringResource(CommonR.string.common_back), onBack)
        },
        actions = {
            ManyakNeutralButton(
                stringResource(NotificationR.string.inbox_read_all),
                onReadAll,
                compact = true,
                enabled =
                    state.unread.total > 0 && !state.isMarkingAllRead,
            )
            ManyakIconButton(
                DesignsystemR.drawable.ic_gear,
                stringResource(NotificationR.string.notification_settings_title),
                onOpenSettings,
            )
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}
