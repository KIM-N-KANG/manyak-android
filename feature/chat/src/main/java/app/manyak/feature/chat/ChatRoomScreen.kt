package app.manyak.feature.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakDestructiveDialog
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.chat.composer.ChatComposer
import app.manyak.feature.chat.composer.ChatComposerActions

/**
 * 채팅방. 셸 없는 전체 화면이며 상세 조회 렌더와 턴 진행, 추천 입력·선택지를 담는다.
 * 재생성·삭제는 다음 단계에서 붙는다.
 */
@Composable
fun ChatRoomScreen(
    chatId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomViewModel =
        hiltViewModel<ChatRoomViewModel, ChatRoomViewModel.Factory>(
            creationCallback = { factory -> factory.create(chatId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val defaultFailure = stringResource(R.string.chat_room_stream_error)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    // 확인 다이얼로그 노출 여부. 구성 변경에서 되돌아가면 안 되는 진행 상태다.
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is ChatRoomEffect.ShowStreamFailure ->
                        Toast.makeText(context, effect.message ?: defaultFailure, Toast.LENGTH_SHORT).show()

                    ChatRoomEffect.ShowCreditRequired ->
                        Toast
                            .makeText(context, R.string.chat_room_credit_required, Toast.LENGTH_SHORT)
                            .show()

                    ChatRoomEffect.ChatDeleted -> {
                        confirmingDelete = false
                        Toast.makeText(context, R.string.chat_room_deleted, Toast.LENGTH_SHORT).show()
                        currentOnDeleted()
                    }

                    ChatRoomEffect.ShowDeleteFailed -> {
                        confirmingDelete = false
                        Toast
                            .makeText(context, R.string.chat_room_delete_failed, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    ChatRoomContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        onDeleteClick = { confirmingDelete = true },
        modifier = modifier,
    )

    if (confirmingDelete) {
        ManyakDestructiveDialog(
            title = stringResource(R.string.chat_room_delete_dialog_title),
            description = stringResource(R.string.chat_room_delete_dialog_description),
            confirmLabel = stringResource(R.string.chat_room_delete),
            cancelLabel = stringResource(R.string.chat_room_delete_dialog_cancel),
            onConfirm = { viewModel.onIntent(ChatRoomIntent.DeleteConfirmed) },
            onDismiss = { if (!state.isDeleting) confirmingDelete = false },
            inProgress = state.isDeleting,
        )
    }
}

@Composable
private fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onIntent: (ChatRoomIntent) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)
    // 덮어쓰기 확인 대상. 구성 변경에서 되돌아가면 안 되는 진행 상태다.
    var pendingFill by rememberSaveable { mutableStateOf<Int?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val phase = chatRoomPhase(state)
        // 헤더는 어느 상태에서나 남는다 — 실패 화면에서도 뒤로 나갈 곳이 있어야 한다. 다만 방을 아직
        // 열지 못한 상태에서는 삭제를 권하지 않는다.
        ChatRoomHeader(
            title = state.storyTitle,
            showsOptions = phase == ChatRoomPhase.CONTENT,
            onBack = onBack,
            onDeleteClick = onDeleteClick,
        )
        val millis = ManyakTheme.motion.screenTransitionMillis
        AnimatedContent(
            modifier = Modifier.weight(1f),
            targetState = phase,
            // 앞 화면이 다 빠진 뒤에 다음 화면이 든다 — 둘이 겹쳐 보이면 어느 쪽이 지금인지 흐려진다.
            transitionSpec = { fadeIn(tween(millis, delayMillis = millis)) togetherWith fadeOut(tween(millis)) },
            label = "chat-room-phase",
        ) { phase ->
            Column(modifier = Modifier.fillMaxSize()) {
                when (phase) {
                    ChatRoomPhase.LOADING ->
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (showProgress) ManyakProgressIndicator()
                        }

                    ChatRoomPhase.FAILED ->
                        ChatRoomLoadFailed(
                            modifier = Modifier.weight(1f),
                            onRetry = { onIntent(ChatRoomIntent.Retry) },
                        )

                    ChatRoomPhase.CONTENT ->
                        ChatRoomLoaded(
                            state = state,
                            onIntent = onIntent,
                            onFillRequested = { position -> pendingFill = position },
                        )
                }
            }
        }
    }

    val fillPosition = pendingFill
    if (fillPosition != null) {
        ReplaceDraftDialog(
            onDismiss = { pendingFill = null },
            onConfirm = {
                pendingFill = null
                onIntent(ChatRoomIntent.SuggestionFilled(fillPosition))
            },
        )
    }
}

/** 헤더 아래에 그릴 화면. 페이드로 교체하므로 상태 조합이 아니라 하나의 값으로 좁힌다. */
private enum class ChatRoomPhase {
    LOADING,
    FAILED,
    CONTENT,
}

private fun chatRoomPhase(state: ChatRoomUiState): ChatRoomPhase =
    when {
        state.isLoading -> ChatRoomPhase.LOADING
        state.loadFailed -> ChatRoomPhase.FAILED
        else -> ChatRoomPhase.CONTENT
    }

/** 조회에 성공한 방. 목록이 남는 자리를 다 쓰고 컴포저가 그 아래에 선다. */
@Composable
private fun ColumnScope.ChatRoomLoaded(
    state: ChatRoomUiState,
    onIntent: (ChatRoomIntent) -> Unit,
    onFillRequested: (Int) -> Unit,
) {
    ChatTranscript(
        modifier = Modifier.weight(1f),
        state = state,
        // 초안이 있으면 채우기 전에 확인을 받는다. 즉시 전송에는 이 확인이 없다.
        onIntent = { intent ->
            if (intent is ChatRoomIntent.SuggestionFilled && state.composer.hasInput) {
                onFillRequested(intent.position)
            } else {
                onIntent(intent)
            }
        },
    )
    ChatComposer(
        state = state.composer,
        choicesEnabled = state.choicesEnabled,
        hasSuggestions = state.suggestions.hasCandidate,
        isStreaming = state.isStreaming,
        actions = composerActions(onIntent),
    )
}

private fun composerActions(onIntent: (ChatRoomIntent) -> Unit): ChatComposerActions =
    ChatComposerActions(
        onPlainTextChange = { text -> onIntent(ChatRoomIntent.PlainTextChanged(text)) },
        onBlockValueChange = { id, value -> onIntent(ChatRoomIntent.BlockValueChanged(id, value)) },
        onAddBlock = { type -> onIntent(ChatRoomIntent.BlockAdded(type)) },
        onRemoveBlock = { id -> onIntent(ChatRoomIntent.BlockRemoved(id)) },
        onModeChange = { mode -> onIntent(ChatRoomIntent.InputModeChanged(mode)) },
        onChoicesEnabledChange = { enabled -> onIntent(ChatRoomIntent.ChoicesEnabledChanged(enabled)) },
        onSend = { onIntent(ChatRoomIntent.Sent) },
        onSendRandomSuggestion = { onIntent(ChatRoomIntent.RandomSuggestionSent) },
    )

/** 채우기가 쓰던 초안을 덮어쓰기 전에 묻는다. */
@Composable
private fun ReplaceDraftDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ManyakDestructiveDialog(
        title = stringResource(R.string.chat_room_fill_confirm_title),
        description = stringResource(R.string.chat_room_fill_confirm_description),
        confirmLabel = stringResource(R.string.chat_room_fill_confirm_confirm),
        cancelLabel = stringResource(R.string.chat_room_fill_confirm_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomHeader(
    title: String,
    showsOptions: Boolean,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.common_back),
                    tint = ManyakTheme.colors.text,
                )
            }
        },
        // 오른쪽에는 옵션 메뉴 하나만 둔다 — 웹 헤더의 공유 버튼은 앱 범위 밖이다.
        actions = { if (showsOptions) ChatRoomOptionsMenu(onDelete = onDeleteClick) },
        // 화면 루트에서 적용한 safeDrawing 인셋이 중복되지 않게 한다.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}

private fun previewChatRoomState(): ChatRoomUiState =
    ChatRoomUiState(
        isLoading = false,
        storyTitle = "두 번째 시계공",
        prologue = "*낡은 시계탑 아래, 도시는 오늘도 같은 시간에 멈춘다.* 당신은 열쇠를 쥔 채 문 앞에 선다.",
        turns =
            listOf(
                ChatRoomTurn(
                    id = 1,
                    userInput = "태엽을 되감는다.",
                    aiOutput = "시계탑이 멈추고 도시가 숨을 고른다.",
                    reachedEnding = "멈춘 도시",
                ),
                ChatRoomTurn(
                    id = 2,
                    userInput = "문을 천천히 연다.",
                    aiOutput = "문이 열리자 **태엽 감기는 소리**가 쏟아진다. *심장이 빨라진다.*",
                    choices = listOf("*문이 삐걱인다* 누구세요?", "조용히 뒤로 물러난다"),
                ),
            ),
    )

@Preview(showBackground = true, name = "채팅방 · 라이트")
@Composable
private fun ChatRoomScreenPreview() {
    ManyakTheme(darkTheme = false) {
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {}, onDeleteClick = {})
    }
}

@Preview(showBackground = true, name = "채팅방 · 다크")
@Composable
private fun ChatRoomScreenDarkPreview() {
    ManyakTheme(darkTheme = true) {
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {}, onDeleteClick = {})
    }
}
