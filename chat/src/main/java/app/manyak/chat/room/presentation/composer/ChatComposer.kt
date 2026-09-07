package app.manyak.chat.room.presentation.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.chat.entity.ChatInputMode
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.chat.R as ChatR

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
    // 입력창이 아니라 컴포저가 편집 상태를 든다.
    val plainState = rememberSyncedTextFieldState(state.plainText, actions.onPlainTextChange)

    val toolbar: @Composable (Modifier) -> Unit = { toolbarModifier ->
        ComposerToolbar(
            modifier = toolbarModifier,
            mode = state.mode,
            choicesEnabled = choicesEnabled,
            canAddBlock = state.blocks.canAddBlock(),
            enabled = !isStreaming,
            sendState = sendState,
            actions = actions,
            onInsertEmphasis = { plainState.wrapSelectionWithEmphasis() },
            onSend = onSend,
        )
    }

    when (state.mode) {
        ChatInputMode.BLOCK ->
            BlockComposer(
                modifier = modifier,
                blocks = state.blocks,
                enabled = !isStreaming,
                actions = actions,
                toolbar = toolbar,
            )

        ChatInputMode.PLAIN ->
            PlainComposer(
                modifier = modifier,
                state = plainState,
                enabled = !isStreaming,
                onDisabledTap = actions.onLockedTap,
                toolbar = toolbar,
            )
    }
}

/** 일반 모드. 입력창과 툴바를 하나의 테두리가 함께 감싼다. */
@Composable
private fun PlainComposer(
    state: TextFieldState,
    enabled: Boolean,
    onDisabledTap: () -> Unit,
    toolbar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ManyakTheme.shapes.card
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // 포커스가 들어오면 테두리를 진하게 올린다 — 스토리 제작 입력과 같은 규칙이다.
    val borderColor = if (focused) ManyakTheme.colors.borderInput else ManyakTheme.colors.border
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    top = ManyakTheme.spacing.compact,
                    bottom = ManyakTheme.spacing.gutter,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(ManyakTheme.colors.surfaceRaised)
                    .border(ComposerBorderWidth, borderColor, shape),
        ) {
            PlainInput(
                state = state,
                enabled = enabled,
                onDisabledTap = onDisabledTap,
                interactionSource = interactionSource,
            )
            // 상자 안쪽 여백은 입력 글자(14)보다 좁아 툴바가 테두리에 더 가깝다.
            toolbar(Modifier.padding(ManyakTheme.spacing.controlVertical))
        }
    }
}

@Composable
private fun PlainInput(
    state: TextFieldState,
    enabled: Boolean,
    onDisabledTap: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * PLAIN_INPUT_HEIGHT_FRACTION

    ComposerTextField(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        state = state,
        placeholder = stringResource(ChatR.string.chat_composer_plain_placeholder),
        enabled = enabled,
        onDisabledTap = onDisabledTap,
        // 상자는 툴바까지 감싸는 바깥 Column 이 그린다.
        containerShape = null,
        interactionSource = interactionSource,
        // 아래 여백은 이어 붙는 툴바가 갖는다 — 입력 글자와 툴바 사이가 두 번 벌어지지 않게 한다.
        contentPadding =
            PaddingValues(
                start = ManyakTheme.spacing.controlHorizontal,
                end = ManyakTheme.spacing.controlHorizontal,
                top = ManyakTheme.spacing.controlVertical,
            ),
    )
}

/** 문자열 상태를 편집 상태로 감싼 입력창. */
@Composable
internal fun SyncedTextField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textColor: androidx.compose.ui.graphics.Color = ManyakTheme.colors.text,
    leading: (@Composable () -> Unit)? = null,
    onDisabledTap: (() -> Unit)? = null,
) {
    ComposerTextField(
        modifier = modifier,
        state = rememberSyncedTextFieldState(text, onTextChange),
        placeholder = placeholder,
        enabled = enabled,
        onDisabledTap = onDisabledTap,
        maxLines = maxLines,
        textColor = textColor,
        leading = leading,
    )
}

/**
 * 문자열 상태와 입력창의 편집 상태를 잇는다.
 *
 * 커서 위치는 화면 표현이라 상태에 올리지 않는다. 다만 밖에서 값이 바뀌는 경로(모드 전환·추천
 * 채우기·전송 후 비우기)가 있어, 값이 갈리면 커서를 끝에 둔 새 값으로 맞춘다.
 *
 * 값을 **편집 상태에 직접 써넣는다** — 문자열만 갈아 끼우면 IME 가 조합 중이던 글자를 그대로
 * 되돌려 보내, 보낸 뒤에도 입력창에 쓰던 글자가 남는다.
 */
@Composable
private fun rememberSyncedTextFieldState(
    text: String,
    onTextChange: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(text)
    val latestText by rememberUpdatedState(text)
    val latestOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(state) {
        // 밖에서 써넣은 값은 되돌려 보내지 않는다 — 같은 값이 오가며 한 바퀴 더 도는 것을 막는다.
        snapshotFlow { state.text.toString() }.collect { edited ->
            if (edited != latestText) latestOnTextChange(edited)
        }
    }
    LaunchedEffect(text) {
        if (state.text.toString() != text) state.setTextAndPlaceCursorAtEnd(text)
    }
    return state
}

/** 고른 구간을 강조 마커로 감싼다. 커서를 되돌려 놓아야 감싼 뒤에도 이어 쓸 수 있다. */
private fun TextFieldState.wrapSelectionWithEmphasis() {
    edit {
        val inserted =
            insertEmphasisMarkers(
                text = toString(),
                selectionStart = selection.start,
                selectionEnd = selection.end,
            )
        replace(0, length, inserted.text)
        selection = TextRange(inserted.selectionStart, inserted.selectionEnd)
    }
}

/** 일반 입력창이 창에서 차지할 수 있는 최대 비율. */
private const val PLAIN_INPUT_HEIGHT_FRACTION = 0.2f

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
        onLockedTap = {},
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
