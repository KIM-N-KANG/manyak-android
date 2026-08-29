package app.manyak.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.chat.ChatInputMode
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 채팅방 하단 컴포저.
 *
 * [hasSuggestions] 를 목록이 아니라 **불리언으로 받는다** — 추천 문구를 그리는 곳은 메시지 영역이고,
 * 컴포저는 "무작위로 보낼 것이 있는가"만 알면 된다.
 */
@Composable
internal fun ChatComposer(
    state: ChatComposerState,
    choicesEnabled: Boolean,
    hasSuggestions: Boolean,
    isStreaming: Boolean,
    actions: ChatComposerActions,
    modifier: Modifier = Modifier,
) {
    val sendState =
        sendButtonState(
            hasInput = state.hasInput,
            hasSuggestions = hasSuggestions,
            choicesEnabled = choicesEnabled,
            isStreaming = isStreaming,
        )
    val onSend: () -> Unit = { if (state.hasInput) actions.onSend() else actions.onSendRandomSuggestion() }

    // 커서 위치는 화면 표현이라 상태에 올리지 않는다. 다만 "상황 추가"가 고른 구간을 감싸야 해서
    // 입력창이 아니라 컴포저가 편집 값을 든다.
    var plainValue by remember { mutableStateOf(state.plainText.asTextFieldValue()) }
    LaunchedEffect(state.plainText) {
        if (plainValue.text != state.plainText) plainValue = state.plainText.asTextFieldValue()
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        when (state.mode) {
            ChatInputMode.BLOCK ->
                BlockInputList(
                    blocks = state.blocks,
                    enabled = !isStreaming,
                    onValueChange = actions.onBlockValueChange,
                    onRemoveBlock = actions.onRemoveBlock,
                )

            ChatInputMode.PLAIN ->
                PlainInput(
                    value = plainValue,
                    enabled = !isStreaming,
                    onValueChange = { next ->
                        plainValue = next
                        if (next.text != state.plainText) actions.onPlainTextChange(next.text)
                    },
                )
        }
        ComposerToolbar(
            mode = state.mode,
            choicesEnabled = choicesEnabled,
            enabled = !isStreaming,
            sendState = sendState,
            actions = actions,
            onInsertEmphasis = {
                val inserted =
                    insertEmphasisMarkers(
                        text = plainValue.text,
                        selectionStart = plainValue.selection.start,
                        selectionEnd = plainValue.selection.end,
                    )
                plainValue =
                    TextFieldValue(
                        text = inserted.text,
                        selection = TextRange(inserted.selectionStart, inserted.selectionEnd),
                    )
                actions.onPlainTextChange(inserted.text)
            },
            onSend = onSend,
        )
    }
}

/**
 * 블럭 목록. 창 높이의 [BLOCK_LIST_HEIGHT_FRACTION] 을 넘으면 그 안에서 스크롤한다 — 키보드가 올라온
 * 상태에서 목록이 화면을 다 먹지 않게 하는 상한이고, 시스템 글자 크기를 키우면 줄 수보다 이 상한이
 * 먼저 걸린다.
 */
@Composable
private fun BlockInputList(
    blocks: List<InputBlock>,
    enabled: Boolean,
    onValueChange: (Long, String) -> Unit,
    onRemoveBlock: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 내용이 있는 블럭을 지울 때만 묻는다. 확인 대상은 회전에서 사라지면 안 되므로 saveable 이다.
    var pendingRemoveId by rememberSaveable { mutableStateOf<Long?>(null) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * BLOCK_LIST_HEIGHT_FRACTION

    Column(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        blocks.forEach { block ->
            BlockInputRow(
                block = block,
                enabled = enabled,
                onValueChange = { value -> onValueChange(block.id, value) },
                onRemove = {
                    if (block.value.isBlank()) onRemoveBlock(block.id) else pendingRemoveId = block.id
                },
            )
        }
    }

    val removeId = pendingRemoveId
    if (removeId != null) {
        RemoveBlockDialog(
            onDismiss = { pendingRemoveId = null },
            onConfirm = {
                pendingRemoveId = null
                onRemoveBlock(removeId)
            },
        )
    }
}

@Composable
private fun BlockInputRow(
    block: InputBlock,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val isSituation = block.type == InputBlockType.SITUATION
    // 라벨과 입력 글자를 같은 색으로 둔다 — 쓰는 중에도 상황이 강조로 나갈 것임이 보여야 한다.
    val contentColor = if (isSituation) ManyakTheme.colors.textSubtle else ManyakTheme.colors.text
    val label =
        stringResource(
            if (isSituation) {
                R.string.chat_composer_situation_label
            } else {
                R.string.chat_composer_dialogue_label
            },
        )
    val placeholder =
        stringResource(
            if (isSituation) {
                R.string.chat_composer_situation_placeholder
            } else {
                R.string.chat_composer_dialogue_placeholder
            },
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalAlignment = Alignment.Top,
    ) {
        SyncedTextField(
            modifier = Modifier.weight(1f),
            text = block.value,
            onTextChange = onValueChange,
            placeholder = placeholder,
            enabled = enabled,
            maxLines = BLOCK_MAX_LINES,
            textColor = contentColor,
            leading = {
                Text(
                    modifier = Modifier.width(BlockLabelWidth),
                    text = label,
                    style = ManyakTheme.typography.labelSmall,
                    color = contentColor,
                )
            },
        )
        ComposerIconButton(
            // 포커스 순서에서 빼 블럭 사이를 키보드로 바로 오가게 한다.
            modifier = Modifier.focusProperties { canFocus = false },
            iconRes = R.drawable.ic_close,
            contentDescription = stringResource(R.string.chat_composer_remove_block),
            enabled = enabled,
            onClick = onRemove,
        )
    }
}

@Composable
private fun PlainInput(
    value: TextFieldValue,
    enabled: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * PLAIN_INPUT_HEIGHT_FRACTION
    ComposerTextField(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        value = value,
        onValueChange = onValueChange,
        placeholder = stringResource(R.string.chat_composer_plain_placeholder),
        enabled = enabled,
    )
}

/**
 * 문자열 상태를 편집 값으로 감싼 입력창.
 *
 * 커서 위치는 화면 표현이라 상태에 올리지 않는다. 다만 밖에서 값이 바뀌는 경로(모드 전환·추천
 * 채우기·전송 후 비우기)가 있어, 값이 갈리면 커서를 끝에 둔 새 값으로 맞춘다.
 */
@Composable
private fun SyncedTextField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textColor: androidx.compose.ui.graphics.Color = ManyakTheme.colors.text,
    leading: (@Composable () -> Unit)? = null,
) {
    var value by remember { mutableStateOf(text.asTextFieldValue()) }
    LaunchedEffect(text) {
        if (value.text != text) value = text.asTextFieldValue()
    }
    ComposerTextField(
        modifier = modifier,
        value = value,
        onValueChange = { next ->
            value = next
            if (next.text != text) onTextChange(next.text)
        },
        placeholder = placeholder,
        enabled = enabled,
        maxLines = maxLines,
        textColor = textColor,
        leading = leading,
    )
}

/** 블럭 목록이 창에서 차지할 수 있는 최대 비율. */
private const val BLOCK_LIST_HEIGHT_FRACTION = 0.3f

/** 일반 입력창이 창에서 차지할 수 있는 최대 비율. */
private const val PLAIN_INPUT_HEIGHT_FRACTION = 0.2f

private const val BLOCK_MAX_LINES = 4

private val BlockLabelWidth = 28.dp

private fun previewActions(): ChatComposerActions =
    ChatComposerActions(
        onPlainTextChange = {},
        onBlockValueChange = { _, _ -> },
        onAddBlock = {},
        onRemoveBlock = {},
        onModeChange = {},
        onChoicesEnabledChange = {},
        onSend = {},
        onSendRandomSuggestion = {},
    )

@Preview(showBackground = true, name = "컴포저 · 블럭 모드")
@Composable
private fun ChatComposerBlockPreview() {
    ManyakTheme(darkTheme = false) {
        ChatComposer(
            state =
                ChatComposerState(
                    mode = ChatInputMode.BLOCK,
                    blocks =
                        listOf(
                            InputBlock(1, InputBlockType.SITUATION, "문이 천천히 열린다"),
                            InputBlock(2, InputBlockType.DIALOGUE, ""),
                        ),
                ),
            choicesEnabled = true,
            hasSuggestions = true,
            isStreaming = false,
            actions = previewActions(),
        )
    }
}

@Preview(showBackground = true, name = "컴포저 · 일반 모드")
@Composable
private fun ChatComposerPlainPreview() {
    ManyakTheme(darkTheme = false) {
        ChatComposer(
            state = ChatComposerState(mode = ChatInputMode.PLAIN, plainText = ""),
            choicesEnabled = true,
            hasSuggestions = true,
            isStreaming = false,
            actions = previewActions(),
        )
    }
}

@Preview(showBackground = true, name = "컴포저 · 응답 대기")
@Composable
private fun ChatComposerStreamingPreview() {
    ManyakTheme(darkTheme = false) {
        ChatComposer(
            state = ChatComposerState(mode = ChatInputMode.PLAIN, plainText = "문을 연다"),
            choicesEnabled = true,
            hasSuggestions = true,
            isStreaming = true,
            actions = previewActions(),
        )
    }
}
