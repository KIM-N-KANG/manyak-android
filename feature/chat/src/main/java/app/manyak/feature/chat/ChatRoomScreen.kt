package app.manyak.feature.chat

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.manyak.core.ui.R
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
    // 채우기가 일어난 횟수. 회전에서 다시 세지 않아 되돌아온 화면이 키보드를 다시 올리지 않는다.
    var fillSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is ChatRoomEffect.ShowStreamFailure ->
                        Toast.makeText(context, effect.message ?: defaultFailure, Toast.LENGTH_SHORT).show()

                    ChatRoomEffect.ComposerFilled -> fillSignal++
                }
            }
        }
    }

    ChatRoomContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        fillSignal = fillSignal,
        modifier = modifier,
    )
}

@Composable
private fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onIntent: (ChatRoomIntent) -> Unit,
    modifier: Modifier = Modifier,
    fillSignal: Int = 0,
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
        ChatRoomHeader(title = state.storyTitle, onBack = onBack)
        when {
            state.isLoading ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showProgress) ManyakProgressIndicator()
                }

            state.loadFailed ->
                ChatRoomLoadFailed(
                    modifier = Modifier.weight(1f),
                    onRetry = { onIntent(ChatRoomIntent.Retry) },
                )

            else -> {
                ChatTranscript(
                    modifier = Modifier.weight(1f),
                    state = state,
                    // 초안이 있으면 채우기 전에 확인을 받는다. 즉시 전송에는 이 확인이 없다.
                    onIntent = { intent ->
                        if (intent is ChatRoomIntent.SuggestionFilled && state.composer.hasInput) {
                            pendingFill = intent.position
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
                    fillSignal = fillSignal,
                )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.chat_room_fill_confirm_title)) },
        text = { Text(text = stringResource(R.string.chat_room_fill_confirm_description)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.chat_room_fill_confirm_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.chat_room_fill_confirm_cancel))
            }
        },
        containerColor = ManyakTheme.colors.surfaceRaised,
        titleContentColor = ManyakTheme.colors.text,
        textContentColor = ManyakTheme.colors.textSubtle,
        shape = ManyakTheme.shapes.card,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = ManyakTheme.typography.titleMedium,
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
        // 화면 루트에서 적용한 safeDrawing 인셋이 중복되지 않게 한다.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = ManyakTheme.colors.surface,
                titleContentColor = ManyakTheme.colors.text,
            ),
    )
}

@Composable
private fun ChatRoomLoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.chat_room_load_error),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.heightIn(min = ManyakTheme.sizes.control),
            onClick = onRetry,
            shape = ManyakTheme.shapes.control,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = ManyakTheme.colors.brand,
                    contentColor = ManyakTheme.colors.textInverse,
                ),
        ) {
            Text(text = stringResource(R.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
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
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {})
    }
}

@Preview(showBackground = true, name = "채팅방 · 다크")
@Composable
private fun ChatRoomScreenDarkPreview() {
    ManyakTheme(darkTheme = true) {
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {})
    }
}

@Preview(showBackground = true, name = "채팅방 · 로드 실패")
@Composable
private fun ChatRoomScreenLoadFailedPreview() {
    ManyakTheme(darkTheme = false) {
        ChatRoomContent(
            state = ChatRoomUiState(isLoading = false, loadFailed = true),
            onBack = {},
            onIntent = {},
        )
    }
}
