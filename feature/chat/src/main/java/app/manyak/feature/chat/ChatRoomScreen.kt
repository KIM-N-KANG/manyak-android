package app.manyak.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.chat.message.ChatAiOutput
import app.manyak.feature.chat.message.ChatUserBand
import kotlinx.coroutines.launch

/**
 * 채팅방. 셸 없는 전체 화면이며, 지금은 상세 조회 렌더(제목·프롤로그·턴 이력)까지 담는다.
 * 입력 컴포저와 턴 진행(SSE)은 다음 단계에서 붙는다.
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

            else -> ChatTranscript(modifier = Modifier.weight(1f), state = state)
        }
    }
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

/**
 * 렌더 순서는 프롤로그 → 각 턴(사용자 밴드 → AI 출력)이다.
 *
 * **항목 사이에 간격을 두지 않는다** — 각 덩이가 스스로 위아래 여백을 갖고, 사용자 밴드의 배경이
 * 시작과 끝을 말한다. 여기에 목록 간격을 더하면 배경 밴드가 본문에서 떠 버린다.
 */
@Composable
private fun ChatTranscript(
    state: ChatRoomUiState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hasPrologue = state.prologue.isNotBlank()
    // 프롤로그 + 턴 + 하단 여백 자리.
    val itemCount = (if (hasPrologue) 1 else 0) + state.turns.size + 1

    EnterAtLastMessage(listState = listState, itemCount = itemCount, hasTurns = state.turns.isNotEmpty())

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
            if (hasPrologue) {
                item(key = "prologue") { ChatAiOutput(content = state.prologue) }
            }
            items(state.turns, key = { turn -> turn.id }) { turn ->
                Column {
                    if (turn.userInput.isNotBlank()) ChatUserBand(text = turn.userInput)
                    if (turn.aiOutput.isNotBlank()) ChatAiOutput(content = turn.aiOutput)
                }
            }
            item(key = "bottom") { Spacer(modifier = Modifier.height(ManyakTheme.spacing.screenBottom)) }
        }
        if (listState.canScrollForward) {
            ScrollToBottomButton(
                modifier = Modifier.align(Alignment.BottomCenter).padding(ManyakTheme.spacing.gutter),
                onClick = { scope.launch { listState.animateScrollToItem(itemCount - 1) } },
            )
        }
    }
}

/**
 * 진입 스크롤 위치. 턴이 있는 방은 마지막 메시지에서, 턴이 0개인 방은 프롤로그가 보이는 상단에서
 * 시작한다.
 *
 * 한 번만 맞추고 그 뒤로는 손대지 않는다 — 읽던 자리를 조회 결과가 바뀔 때마다 끌어내리면 안 된다.
 */
@Composable
private fun EnterAtLastMessage(
    listState: LazyListState,
    itemCount: Int,
    hasTurns: Boolean,
) {
    var settled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(itemCount, hasTurns) {
        if (settled) return@LaunchedEffect
        if (hasTurns) listState.scrollToItem(itemCount - 1)
        settled = true
    }
}

@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.chat_room_scroll_to_bottom)
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.control)
                .border(ButtonBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.pill)
                .clip(ManyakTheme.shapes.pill)
                .background(ManyakTheme.colors.surfaceRaised)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(R.drawable.ic_angle_down),
            contentDescription = label,
            tint = ManyakTheme.colors.text,
        )
    }
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

private val ButtonBorderWidth = 1.dp

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
