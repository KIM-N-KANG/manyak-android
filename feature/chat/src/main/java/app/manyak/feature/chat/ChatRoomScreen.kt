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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * 채팅방. 셸 없는 전체 화면이며 상세 조회 렌더와 턴 진행을 담는다.
 * 추천 입력·선택지와 재생성·삭제는 다음 단계에서 붙는다.
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

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is ChatRoomEffect.ShowStreamFailure ->
                        Toast.makeText(context, effect.message ?: defaultFailure, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ChatRoomContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onIntent: (ChatRoomIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)

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
                ChatTranscript(modifier = Modifier.weight(1f), state = state)
                ChatComposer(
                    state = state.composer,
                    choicesEnabled = state.choicesEnabled,
                    // 추천 입력은 다음 단계에서 붙는다. 지금은 보낼 후보가 없다.
                    hasSuggestions = false,
                    isStreaming = state.isStreaming,
                    actions = composerActions(onIntent),
                )
            }
        }
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
        onSendRandomSuggestion = {},
    )

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
