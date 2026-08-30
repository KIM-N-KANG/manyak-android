package app.manyak.feature.chat.composer

import app.manyak.core.domain.chat.ChatInputMode

/**
 * 컴포저가 들고 있는 것 전부. ViewModel 이 소유하고 화면은 값과 콜백만 받는다.
 *
 * 두 모드의 값을 함께 들지 않는다 — 모드를 바꿀 때 [convertTo] 로 옮겨 담아 한쪽만 유효하게 둔다.
 * 양쪽을 동시에 들면 어느 쪽이 지금 사용자가 쓰던 것인지 알 수 없다.
 */
data class ChatComposerState(
    val mode: ChatInputMode = ChatInputMode.BLOCK,
    val plainText: String = "",
    val blocks: List<InputBlock> = createDefaultInputBlocks(),
) {
    /** 보낼 것이 있는지. 공백만 있는 입력은 없는 것으로 본다. */
    val hasInput: Boolean
        get() = if (mode == ChatInputMode.BLOCK) blocks.hasInput() else plainText.isNotBlank()

    /** 서버로 보낼 문장. 블럭은 빈 줄로 띄워 잇는다. */
    fun toUserInput(): String =
        if (mode == ChatInputMode.BLOCK) {
            serializeInputBlocks(blocks, BLOCK_SEND_SEPARATOR)
        } else {
            plainText.trim()
        }

    /**
     * 모드를 바꾸면서 쓰던 내용을 옮긴다.
     *
     * 일반 → 블럭에서 결과가 비면 기본 블럭 둘로 되돌린다. 빈 목록으로 두면 입력할 칸이 하나도 없는
     * 컴포저가 된다.
     */
    fun convertTo(nextMode: ChatInputMode): ChatComposerState {
        if (nextMode == mode) return this
        return if (nextMode == ChatInputMode.BLOCK) {
            val parsed = parseInputBlocks(plainText)
            copy(
                mode = nextMode,
                blocks = parsed.ifEmpty { createDefaultInputBlocks() },
                plainText = "",
            )
        } else {
            copy(
                mode = nextMode,
                plainText = serializeInputBlocks(blocks),
                blocks = emptyList(),
            )
        }
    }

    /**
     * 추천 문장을 입력창에 채운다.
     *
     * 블럭 모드는 쓰던 칸을 **통째로 갈아 끼운다** — 뒤에 이어 붙이면 채우기가 추가 입력처럼 보이고,
     * 채운 원문과 전송문을 대조하는 출처 판정도 어긋난다.
     */
    fun filledWith(text: String): ChatComposerState =
        if (mode == ChatInputMode.BLOCK) {
            copy(blocks = parseInputBlocks(text).ifEmpty { createDefaultInputBlocks() })
        } else {
            copy(plainText = text)
        }

    /** 전송에 성공한 뒤의 빈 상태. 모드는 그대로 둔다. */
    fun cleared(): ChatComposerState = copy(plainText = "", blocks = createDefaultInputBlocks())
}

/** 전송 버튼이 지금 무엇을 하는지. 한 버튼이 세 상태를 공유하므로 아이콘이 그것을 말해야 한다. */
enum class SendButtonIcon {
    /** 응답을 받는 중. */
    SPINNER,

    /** 누르면 추천 입력 하나를 무작위로 보낸다. */
    RANDOM,

    /** 누르면 쓴 것을 보낸다. */
    SEND,
}

data class SendButtonState(
    val enabled: Boolean,
    val icon: SendButtonIcon,
)

/**
 * 전송 버튼의 활성 여부와 아이콘.
 *
 * 입력이 없고 추천이 켜져 있으면 **추천이 없어도** 무작위 전송 아이콘을 유지한 채 잠근다. 아이콘이
 * 오갔다 하면 무엇을 누르는 버튼인지 읽히지 않는다.
 */
fun sendButtonState(
    hasInput: Boolean,
    hasSuggestions: Boolean,
    choicesEnabled: Boolean,
    isStreaming: Boolean,
): SendButtonState =
    SendButtonState(
        enabled = !isStreaming && (hasInput || (choicesEnabled && hasSuggestions)),
        icon =
            when {
                isStreaming -> SendButtonIcon.SPINNER
                !hasInput && choicesEnabled -> SendButtonIcon.RANDOM
                else -> SendButtonIcon.SEND
            },
    )
