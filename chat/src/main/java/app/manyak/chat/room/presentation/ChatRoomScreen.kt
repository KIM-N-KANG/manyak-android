package app.manyak.chat.room.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.chat.list.presentation.label
import app.manyak.chat.room.presentation.composer.ChatComposer
import app.manyak.chat.room.presentation.composer.ChatComposerActions
import app.manyak.chat.room.presentation.composer.ChatSettingsSheet
import app.manyak.designsystem.component.FullscreenImageViewer
import app.manyak.designsystem.component.ManyakDestructiveDialog
import app.manyak.designsystem.component.ManyakIconButton
import app.manyak.designsystem.component.ManyakProgressIndicator
import app.manyak.designsystem.component.rememberDelayedProgressVisibility
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR
import app.manyak.common.R as CommonR
import app.manyak.designsystem.R as DesignsystemR

/**
 * 채팅방. 셸 없는 전체 화면이며 상세 조회 렌더와 턴 진행, 추천 입력·선택지, 헤더 메뉴(새 채팅·신고·삭제)를 담는다.
 *
 * @param onReplaceChat 메뉴에서 새 채팅을 만들었다. 지금 방을 걷어내고 그 자리에 새 방을 연다.
 */
@Composable
fun ChatRoomScreen(
    chatId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onReplaceChat: (String) -> Unit,
    onOpenCreditCharge: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatRoomViewModel =
        hiltViewModel<ChatRoomViewModel, ChatRoomViewModel.Factory>(
            creationCallback = { factory -> factory.create(chatId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 메뉴 시트·확인 다이얼로그 노출 여부. 구성 변경에서 되돌아가면 안 되는 진행 상태다.
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    ChatRoomEffects(
        viewModel = viewModel,
        storyTitle = state.storyTitle,
        onDeleted = onDeleted,
        onReplaceChat = onReplaceChat,
        onCloseMenu = { menuOpen = false },
        onCloseDeleteDialog = { confirmingDelete = false },
    )

    ChatRoomContent(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        onOpenMenu = {
            menuOpen = true
            viewModel.onIntent(ChatRoomIntent.MenuOpened)
        },
        modifier = modifier,
    )

    if (menuOpen) {
        ChatMenuSheet(
            state = state,
            onIntent = viewModel::onIntent,
            onDelete = { confirmingDelete = true },
            onOpenCreditCharge = onOpenCreditCharge,
            onDismiss = { menuOpen = false },
        )
    }

    if (confirmingDelete) {
        ChatRoomDeleteDialog(
            isDeleting = state.isDeleting,
            onConfirm = { viewModel.onIntent(ChatRoomIntent.DeleteConfirmed) },
            onDismiss = { if (!state.isDeleting) confirmingDelete = false },
        )
    }

    ChatRoomReportSheet(state = state.report, onIntent = viewModel::onIntent)
}

@Composable
private fun ChatRoomContent(
    state: ChatRoomUiState,
    onBack: () -> Unit,
    onIntent: (ChatRoomIntent) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showProgress = rememberDelayedProgressVisibility(state.isLoading)
    // 덮어쓰기 확인 대상. 구성 변경에서 되돌아가면 안 되는 진행 상태다.
    var pendingFill by rememberSaveable { mutableStateOf<Int?>(null) }
    // 메시지 영역의 빈 곳을 누를 때마다 바뀐다. 구성 변경에서 읽던 화면이 달라지지 않게 남긴다.
    var headerHidden by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 헤더는 목록 위에 겹쳐 뜬다 — 숨기고 보일 때 목록 높이가 바뀌면 읽던 줄이 밀린다.
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val phase = chatRoomPhase(state)
            val millis = ManyakTheme.motion.screenTransitionMillis
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = phase,
                // 앞 화면이 다 빠진 뒤에 다음 화면이 든다 — 둘이 겹쳐 보이면 어느 쪽이 지금인지 흐려진다.
                transitionSpec = { fadeIn(tween(millis, delayMillis = millis)) togetherWith fadeOut(tween(millis)) },
                label = "chat-room-phase",
            ) { phase ->
                // 목록은 헤더 아래까지 깔리고 스스로 위 여백을 둔다. 나머지 상태는 헤더 아래에서 시작한다.
                val top = if (phase == ChatRoomPhase.CONTENT) 0.dp else ChatRoomHeaderHeight
                Column(modifier = Modifier.fillMaxSize().padding(top = top)) {
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
                                onBackgroundTap = { headerHidden = !headerHidden },
                            )
                    }
                }
            }
            ChatRoomHeaderOverlay(
                visible = !headerHidden || phase != ChatRoomPhase.CONTENT,
                title = state.storyTitle,
                phase = phase,
                onBack = onBack,
                onOpenMenu = onOpenMenu,
            )
        }
        FullscreenImageViewer(state.imageViewerUrl, onClose = { onIntent(ChatRoomIntent.CloseImageViewer) })
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
    onBackgroundTap: () -> Unit,
) {
    // 설정 시트 열림. 회전·다크 모드 전환에서 닫히면 사용자가 다시 열어야 한다.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    ChatTranscript(
        modifier = Modifier.weight(1f),
        state = state,
        topInset = ChatRoomHeaderHeight,
        onBackgroundTap = onBackgroundTap,
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
        realtimeImageEnabled = state.realtimeImageEnabled,
        hasSuggestions = state.suggestions.hasCandidate,
        isStreaming = state.isStreaming,
        actions = composerActions(onIntent, onOpenSettings = { settingsOpen = true }),
    )
    if (settingsOpen) {
        // 스위치는 의도로 바로 올라가고 시트는 닫기 전까지 남는다 — 블럭 입력을 끄면 뒤의 컴포저가 바뀐다.
        ChatSettingsSheet(
            realtimeImageEnabled = state.realtimeImageEnabled,
            choicesEnabled = state.choicesEnabled,
            mode = state.composer.mode,
            onRealtimeImageEnabledChange = { enabled -> onIntent(ChatRoomIntent.RealtimeImageEnabledChanged(enabled)) },
            onChoicesEnabledChange = { enabled -> onIntent(ChatRoomIntent.ChoicesEnabledChanged(enabled)) },
            onModeChange = { mode -> onIntent(ChatRoomIntent.InputModeChanged(mode)) },
            onDismiss = { settingsOpen = false },
        )
    }
}

private fun composerActions(
    onIntent: (ChatRoomIntent) -> Unit,
    onOpenSettings: () -> Unit,
): ChatComposerActions =
    ChatComposerActions(
        onPlainTextChange = { text -> onIntent(ChatRoomIntent.PlainTextChanged(text)) },
        onBlockValueChange = { id, value -> onIntent(ChatRoomIntent.BlockValueChanged(id, value)) },
        onAddBlock = { type -> onIntent(ChatRoomIntent.BlockAdded(type)) },
        onRemoveBlock = { id -> onIntent(ChatRoomIntent.BlockRemoved(id)) },
        onOpenSettings = onOpenSettings,
        onSend = { onIntent(ChatRoomIntent.Sent) },
        onSendRandomSuggestion = { onIntent(ChatRoomIntent.RandomSuggestionSent) },
        onLockedTap = { onIntent(ChatRoomIntent.LockedComposerTapped) },
    )

/** 채우기가 쓰던 초안을 덮어쓰기 전에 묻는다. */
@Composable
private fun ReplaceDraftDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ManyakDestructiveDialog(
        title = stringResource(ChatR.string.chat_room_fill_confirm_title),
        description = stringResource(ChatR.string.chat_room_fill_confirm_description),
        confirmLabel = stringResource(ChatR.string.chat_room_fill_confirm_confirm),
        cancelLabel = stringResource(ChatR.string.chat_room_fill_confirm_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** 목록 위에 겹쳐 뜬 헤더. 방을 열기 전에는 숨길 수 없다 — 실패 화면에서도 뒤로 나갈 곳이 있어야 한다. */
@Composable
private fun ChatRoomHeaderOverlay(
    visible: Boolean,
    title: String,
    phase: ChatRoomPhase,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val millis = ManyakTheme.motion.elementEnterMillis
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(millis, easing = LinearOutSlowInEasing)),
        exit = fadeOut(tween(millis, easing = LinearOutSlowInEasing)),
    ) {
        // 방을 아직 열지 못한 상태에서는 메뉴를 두지 않는다.
        ChatRoomHeader(
            title = title,
            // 방을 연 뒤에도 제목이 비어 있으면 참조 스토리가 삭제된 것이다 — 목록 카드와 같은 문구로 알린다.
            isStoryDeleted = phase == ChatRoomPhase.CONTENT && title.isBlank(),
            showsMenu = phase == ChatRoomPhase.CONTENT,
            onBack = onBack,
            onOpenMenu = onOpenMenu,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomHeader(
    title: String,
    isStoryDeleted: Boolean,
    showsMenu: Boolean,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = if (isStoryDeleted) stringResource(ChatR.string.chat_list_deleted_story) else title,
                style = ManyakTheme.typography.bodyLargeStrong,
                color = if (isStoryDeleted) ManyakTheme.colors.textSubtlest else ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            ManyakIconButton(
                iconRes = DesignsystemR.drawable.ic_arrow_back,
                contentDescription = stringResource(CommonR.string.common_back),
                onClick = onBack,
            )
        },
        // 오른쪽에는 메뉴 버튼 하나만 둔다 — 웹 헤더의 공유 버튼은 앱 범위 밖이다.
        actions = {
            if (showsMenu) {
                ManyakIconButton(
                    iconRes = DesignsystemR.drawable.ic_more_horizontal,
                    contentDescription = stringResource(ChatR.string.chat_room_menu),
                    onClick = onOpenMenu,
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

@OptIn(ExperimentalMaterial3Api::class)
private val ChatRoomHeaderHeight = TopAppBarDefaults.TopAppBarExpandedHeight

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
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {}, onOpenMenu = {})
    }
}

@Preview(showBackground = true, name = "채팅방 · 다크")
@Composable
private fun ChatRoomScreenDarkPreview() {
    ManyakTheme(darkTheme = true) {
        ChatRoomContent(state = previewChatRoomState(), onBack = {}, onIntent = {}, onOpenMenu = {})
    }
}
