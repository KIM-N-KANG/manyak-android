package app.manyak.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import app.manyak.feature.chat.message.ChatAiOutput
import app.manyak.feature.chat.message.ChatUserBand
import app.manyak.feature.chat.suggestion.ChatSuggestionArea
import app.manyak.feature.chat.suggestion.ChatSuggestions
import app.manyak.feature.chat.suggestion.hasSuggestionArea
import kotlinx.coroutines.launch

/**
 * 렌더 순서는 프롤로그 → 각 턴(사용자 밴드 → AI 출력)이다.
 *
 * **항목 사이에 간격을 두지 않는다** — 각 덩이가 스스로 위아래 여백을 갖고, 사용자 밴드의 배경이
 * 시작과 끝을 말한다. 여기에 목록 간격을 더하면 배경 밴드가 본문에서 떠 버린다.
 */
@Composable
internal fun ChatTranscript(
    state: ChatRoomUiState,
    onIntent: (ChatRoomIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val prologueCount = if (state.prologue.isNotBlank()) 1 else 0
    val streaming = state.streaming
    // 재생성은 대상 턴 자리에서 진행하므로 목록 끝에 블록을 더하지 않는다.
    val appendsStreaming = streaming != null && state.regeneratingTurnId == null
    // 진행 중에는 추천을 그리지 않는다 — 이미 보낸 뒤라 고를 것이 아니다.
    val showsSuggestions = streaming == null && hasSuggestionArea(state)
    val itemCount =
        prologueCount + state.turns.size + (if (appendsStreaming) 1 else 0) +
            (if (showsSuggestions) 1 else 0) + 1

    EnterAtLastMessage(listState = listState, itemCount = itemCount, hasTurns = state.turns.isNotEmpty())
    AnchorStreamingTurn(listState = listState, state = state, itemCount = itemCount, prologueCount = prologueCount)

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.fillMaxWidth(), state = listState) {
            if (prologueCount > 0) {
                item(key = "prologue") { ChatAiOutput(content = state.prologue) }
            }
            itemsIndexed(state.turns, key = { _, turn -> turn.id }) { index, turn ->
                if (streaming != null && turn.id == state.regeneratingTurnId) {
                    StreamingBlock(streaming = streaming)
                } else {
                    TurnBlock(
                        turn = turn,
                        isLast = streaming == null && index == state.turns.lastIndex,
                        onRegenerate = { onIntent(ChatRoomIntent.RegenerateRequested(turn.id)) },
                    )
                }
            }
            if (appendsStreaming && streaming != null) {
                item(key = "streaming") { StreamingBlock(streaming = streaming) }
            }
            if (showsSuggestions) suggestionItem(state, state.suggestions, state.turns.lastOrNull()?.id, onIntent)
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

/** 확정 턴 하나. 재생성 버튼은 **마지막 턴에만** 붙는다 — 앞선 턴을 바꾸면 뒤 이야기와 어긋난다. */
@Composable
private fun TurnBlock(
    turn: ChatRoomTurn,
    isLast: Boolean,
    onRegenerate: () -> Unit,
) {
    Column {
        if (turn.userInput.isNotBlank()) ChatUserBand(text = turn.userInput)
        if (turn.aiOutput.isNotBlank()) {
            ChatAiOutput(content = turn.aiOutput, endingName = turn.reachedEnding)
        }
        if (isLast && canRegenerate(turn)) RegenerateButton(onClick = onRegenerate)
    }
}

/** 진행 중인 턴. 이어쓰기면 목록 끝에, 재생성이면 대상 턴 자리에 놓인다. */
@Composable
private fun StreamingBlock(streaming: StreamingTurn) {
    Column {
        ChatUserBand(text = streaming.userInput)
        if (streaming.segments.isEmpty()) {
            WritingPlaceholder()
        } else {
            ChatAiOutput(segments = streaming.segments)
        }
    }
}

private fun LazyListScope.suggestionItem(
    state: ChatRoomUiState,
    suggestions: ChatSuggestions,
    lastTurnId: Long?,
    onIntent: (ChatRoomIntent) -> Unit,
) {
    item(key = "suggestions") {
        ChatSuggestionArea(
            suggestions = suggestions,
            progress = state.choicesProgress,
            lastTurnId = lastTurnId,
            choicesEnabled = state.choicesEnabled,
            showsHint = state.choicesHintUnseen && state.turns.isEmpty(),
            onSend = { position -> onIntent(ChatRoomIntent.SuggestionSent(position)) },
            onFill = { position -> onIntent(ChatRoomIntent.SuggestionFilled(position)) },
            onRetry = { onIntent(ChatRoomIntent.ChoicesRetried) },
        )
    }
}

private fun hasSuggestionArea(state: ChatRoomUiState): Boolean =
    hasSuggestionArea(
        suggestions = state.suggestions,
        progress = state.choicesProgress,
        lastTurnId = state.turns.lastOrNull()?.id,
        choicesEnabled = state.choicesEnabled,
    )

/**
 * 진행 중인 턴을 뷰포트 맨 위에 붙인다 — 조각이 늘어도 읽던 자리가 끌려 내려가지 않는다.
 *
 * 재생성은 목록 끝이 아니라 **대상 턴 자리**를 맞춘다. 바뀌는 곳이 화면 밖이면 무엇이 다시 만들어지는지
 * 보이지 않는다.
 */
@Composable
private fun AnchorStreamingTurn(
    listState: LazyListState,
    state: ChatRoomUiState,
    itemCount: Int,
    prologueCount: Int,
) {
    LaunchedEffect(state.isStreaming) {
        if (!state.isStreaming) return@LaunchedEffect
        val regenerating = state.regeneratingTurnId
        val index =
            if (regenerating == null) {
                itemCount - 2
            } else {
                prologueCount + state.turns.indexOfFirst { turn -> turn.id == regenerating }
            }
        listState.scrollToItem(index.coerceAtLeast(0))
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

/** 첫 표시 가능 사건이 오기 전의 자리. 빈 화면으로 두면 보냈는지 알 수 없다. */
@Composable
private fun WritingPlaceholder(modifier: Modifier = Modifier) {
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.passage),
        text = stringResource(R.string.chat_room_writing),
        style = ManyakTheme.typography.bodyReading,
        color = ManyakTheme.colors.textSubtlest,
    )
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

private val ButtonBorderWidth = 1.dp
