package app.manyak.chat.room.presentation.composer

import app.manyak.chat.entity.ChatInputMode

/** 컴포저가 위로 올리는 동작. 화면은 이 콜백만 받고 상태를 직접 바꾸지 않는다. */
internal data class ChatComposerActions(
    val onPlainTextChange: (String) -> Unit,
    val onBlockValueChange: (Long, String) -> Unit,
    val onAddBlock: (InputBlockType) -> Unit,
    val onRemoveBlock: (Long) -> Unit,
    val onModeChange: (ChatInputMode) -> Unit,
    val onChoicesEnabledChange: (Boolean) -> Unit,
    val onSend: () -> Unit,
    val onSendRandomSuggestion: () -> Unit,
    /** 잠긴 입력창을 눌렀다. 왜 입력할 수 없는지 알리는 데 쓴다. */
    val onLockedTap: () -> Unit,
)

/** 설정 메뉴 항목 하나. 설명은 라벨만으로 무엇이 달라지는지 알기 어려운 설정에 붙인다. */
internal data class ComposerMenuOption<T>(
    val value: T,
    val label: String,
    val description: String,
)
