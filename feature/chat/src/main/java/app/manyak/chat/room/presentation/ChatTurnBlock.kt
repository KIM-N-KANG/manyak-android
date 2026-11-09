package app.manyak.chat.room.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import app.manyak.chat.room.presentation.message.ChatAiOutput
import app.manyak.chat.room.presentation.message.ChatUserBand

/**
 * 입력 수정이 진행되는 동안은 대상 턴까지만 그린다. 끝나면 그 턴부터 새 턴 하나로 바뀌어, 사라질 턴을 보이면
 * 이어질 이야기처럼 읽힌다.
 */
internal fun visibleTurns(state: ChatRoomUiState): List<ChatRoomTurn> {
    if (state.streaming == null || !state.hidesLaterTurns) return state.turns
    val index = state.turns.indexOfFirst { turn -> turn.id == state.replacingTurnId }
    return if (index == -1) state.turns else state.turns.take(index + 1)
}

/** 입력을 고치는 턴의 자리. 대상 턴이 목록에 없으면 편집기도 흐림도 없어 -1 이다. */
internal fun editingIndexOf(
    state: ChatRoomUiState,
    turns: List<ChatRoomTurn>,
): Int {
    val editing = state.editing ?: return -1
    return turns.indexOfFirst { turn -> turn.id == editing.turnId }
}

/** 고치는 턴의 응답과 그 뒤는 바뀔 내용이라 흐리게 둔다. */
internal fun Modifier.dimmedWhile(dimmed: Boolean): Modifier = if (dimmed) alpha(EDITING_DIM_ALPHA) else this

/** 확정 턴들. 재생성과 입력 수정의 대상 턴은 자리를 지킨 채 진행 블록으로 바뀐다. */
internal fun LazyListScope.turnItems(
    state: ChatRoomUiState,
    turns: List<ChatRoomTurn>,
    editingIndex: Int,
    onIntent: (ChatRoomIntent) -> Unit,
    onCharacterImageClick: (String) -> Unit,
) {
    val streaming = state.streaming
    itemsIndexed(turns, key = { _, turn -> turn.id }) { index, turn ->
        if (streaming != null && turn.id == state.replacingTurnId) {
            StreamingBlock(streaming = streaming, onCharacterImageClick = onCharacterImageClick)
        } else {
            TurnBlock(
                modifier = Modifier.dimmedWhile(editingIndex != -1 && index > editingIndex),
                turn = turn,
                state = state,
                isLast = streaming == null && index == turns.lastIndex,
                editing = state.editing.takeIf { index == editingIndex },
                onIntent = onIntent,
                onCharacterImageClick = onCharacterImageClick,
            )
        }
    }
}

/**
 * 확정 턴 하나. 재생성 버튼은 **마지막 턴에만** 붙고, 입력 수정과 포크는 모든 턴에 붙어 생성 중에 잠긴다.
 * 입력을 고치는 중이면 사용자 입력 자리에 [editing] 의 편집기를 두고 아래 응답을 흐린다.
 */
@Composable
private fun TurnBlock(
    turn: ChatRoomTurn,
    state: ChatRoomUiState,
    isLast: Boolean,
    editing: TurnEditing?,
    onIntent: (ChatRoomIntent) -> Unit,
    onCharacterImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (editing != null) {
            ChatTurnEditor(
                editing = editing,
                original = turn.userInput,
                busy = state.isStreaming,
                onDraftChange = { text -> onIntent(ChatRoomIntent.EditDraftChanged(text)) },
                onCancel = { onIntent(ChatRoomIntent.EditCancelled) },
                onSubmit = { onIntent(ChatRoomIntent.EditSubmitted) },
            )
        } else if (turn.userInput.isNotBlank()) {
            ChatUserBand(text = turn.userInput) {
                EditTurnButton(
                    enabled = !state.isStreaming,
                    onClick = { onIntent(ChatRoomIntent.EditRequested(turn.id)) },
                )
            }
        }
        Column(modifier = Modifier.dimmedWhile(editing != null)) {
            if (turn.aiOutput.isNotBlank()) {
                ChatAiOutput(
                    content = turn.aiOutput,
                    endingName = turn.reachedEnding,
                    onCharacterImageClick = onCharacterImageClick,
                )
            }
            TurnActionRow(
                showsRegenerate = isLast && canRegenerate(turn),
                showsFork = turn.aiOutput.isNotBlank(),
                forkEnabled = !state.isStreaming && !state.isForking,
                onRegenerate = { onIntent(ChatRoomIntent.RegenerateRequested(turn.id)) },
                onFork = { onIntent(ChatRoomIntent.ForkRequested(turn.id)) },
            )
        }
    }
}

/** 입력을 고치는 동안 바뀔 응답과 뒤의 턴을 흐리는 정도. 웹과 같은 40% 다. */
private const val EDITING_DIM_ALPHA = 0.4f
