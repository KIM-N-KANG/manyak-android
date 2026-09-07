package app.manyak.chat.room.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.manyak.chat.list.presentation.label
import app.manyak.chat.room.presentation.message.ChatAiOutput
import app.manyak.chat.room.presentation.message.ChatUserBand
import app.manyak.chat.room.presentation.message.rememberTypewriterSegments
import app.manyak.chat.room.presentation.suggestion.ChatSuggestionArea
import app.manyak.chat.room.presentation.suggestion.ChatSuggestions
import app.manyak.chat.room.presentation.suggestion.hasSuggestionArea
import app.manyak.designsystem.component.ScrollEdgeFade
import app.manyak.designsystem.component.clearFocusOnTap
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.launch
import app.manyak.chat.R as ChatR
import app.manyak.designsystem.R as DesignsystemR

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
    // 보낸 턴이 상단까지 올라갈 자리. 목록 끝 항목이 이 값을 높이로 읽는다. 구성 변경에서 잃으면
    // 목록만 (인덱스, 오프셋)으로 복원돼 자리가 사라지고, 복원한 위치가 콘텐츠 끝에 걸린다.
    val padPx = rememberSaveable { mutableIntStateOf(0) }
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
    AnchorStreamingTurn(
        listState = listState,
        state = state,
        itemCount = itemCount,
        prologueCount = prologueCount,
        padPx = padPx,
    )
    ReclaimAnchorPad(listState = listState, isStreaming = state.isStreaming, padPx = padPx)
    KeepReadingPosition(listState = listState, anchored = state.isStreaming)

    val focusManager = LocalFocusManager.current

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clearFocusOnTap(focusManager),
    ) {
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
            item(key = ANCHOR_PAD_KEY) { AnchorPad(padPx = padPx) }
        }
        // 목록이 컴포저 위 경계에서 잘리는 것을 부드럽게 만든다. 컴포저가 커지면 이 상자가 줄어들어
        // 페이드도 함께 따라 올라간다.
        ScrollEdgeFade(modifier = Modifier.align(Alignment.BottomCenter))
        ScrollToBottomAffordance(
            visible = listState.canScrollForward,
            onClick = { scope.launch { listState.animateScrollToItem(itemCount - 1) } },
        )
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

/**
 * 진행 중인 턴. 이어쓰기면 목록 끝에, 재생성이면 대상 턴 자리에 놓인다.
 *
 * 본문은 도착한 그대로가 아니라 타자기 공개를 거친다 — 배칭된 덩이가 아니라 글자가 이어서 나타난다.
 */
@Composable
private fun StreamingBlock(streaming: StreamingTurn) {
    val revealed = rememberTypewriterSegments(streaming.segments)
    Column {
        ChatUserBand(text = streaming.userInput)
        if (revealed.isEmpty()) {
            WritingPlaceholder()
        } else {
            ChatAiOutput(segments = revealed)
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

/**
 * 목록이 짧아지면 그만큼 본문도 따라 올라간다.
 *
 * 키보드가 올라오면 목록이 차지할 높이가 줄어드는데 스크롤 위치는 위를 기준으로 잡혀 있어, 읽던 자리가
 * 아래로 잘려 나간다. 줄어든 만큼 스크롤을 밀어 **보고 있던 줄이 그대로 보이게** 한다 — 키보드가
 * 내려가거나 컴포저가 다시 짧아지면 같은 값만큼 되돌아간다.
 *
 * **앵커가 잡혀 있는 동안은 멈춘다** — 붙잡아야 할 줄이 아래가 아니라 위(보낸 턴)라서, 여기서 밀면
 * 방금 보낸 턴이 화면 위로 빠져나간다. 앵커가 풀리면 그때 높이를 기준만 다시 잡고 시작한다.
 */
@Composable
private fun KeepReadingPosition(
    listState: LazyListState,
    anchored: Boolean,
) {
    LaunchedEffect(listState, anchored) {
        if (anchored) return@LaunchedEffect
        var previous: Int? = null
        snapshotFlow { listState.layoutInfo.viewportSize.height }.collect { height ->
            val last = previous
            previous = height
            // 첫 값은 기준만 잡는다. 구성 변경으로 다시 시작해도 새 크기가 기준이 돼 화면이 튀지 않는다.
            if (height > 0 && last != null && last != height) listState.scrollBy((last - height).toFloat())
        }
    }
}

/** 첫 표시 가능 사건이 오기 전의 자리. 빈 화면으로 두면 보냈는지 알 수 없어, 옅은 띠를 흘려 진행 중임을 말한다. */
@Composable
private fun WritingPlaceholder(modifier: Modifier = Modifier) {
    val statusLabel = stringResource(ChatR.string.chat_room_writing_status)
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ManyakTheme.spacing.gutter, vertical = ManyakTheme.spacing.passage)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = statusLabel
                },
        text = stringResource(ChatR.string.chat_room_writing),
        style = ManyakTheme.typography.bodyReading.merge(TextStyle(brush = rememberWritingShimmerBrush())),
    )
}

/**
 * 버튼이 가리키는 방향으로 드나든다 — 아래에서 올라와 나타나고, 아래로 내려가며 사라진다. 들어올 땐
 * 감속해 자리를 잡고, 나갈 땐 가속해 비켜난다.
 */
@Composable
private fun BoxScope.ScrollToBottomAffordance(
    visible: Boolean,
    onClick: () -> Unit,
) {
    val enterMillis = ManyakTheme.motion.elementEnterMillis
    val exitMillis = ManyakTheme.motion.elementExitMillis
    AnimatedVisibility(
        modifier = Modifier.align(Alignment.BottomCenter).padding(ManyakTheme.spacing.gutter),
        visible = visible,
        enter =
            slideInVertically(tween(enterMillis, easing = FastOutSlowInEasing)) { height -> height } +
                fadeIn(tween(enterMillis)),
        exit =
            slideOutVertically(tween(exitMillis, easing = FastOutLinearInEasing)) { height -> height } +
                fadeOut(tween(exitMillis)),
    ) {
        ScrollToBottomButton(onClick = onClick)
    }
}

@Composable
private fun ScrollToBottomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(ChatR.string.chat_room_scroll_to_bottom)
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.controlSmall)
                .border(ButtonBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.menuItem)
                .clip(ManyakTheme.shapes.menuItem)
                .background(ManyakTheme.colors.surfaceRaised)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            painter = painterResource(DesignsystemR.drawable.ic_angle_down),
            contentDescription = label,
            tint = ManyakTheme.colors.text,
        )
    }
}

private val ButtonBorderWidth = 1.dp
