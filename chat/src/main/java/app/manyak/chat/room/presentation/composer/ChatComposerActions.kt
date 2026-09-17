package app.manyak.chat.room.presentation.composer

/** 컴포저가 위로 올리는 동작. 화면은 이 콜백만 받고 상태를 직접 바꾸지 않는다. */
internal data class ChatComposerActions(
    val onPlainTextChange: (String) -> Unit,
    val onBlockValueChange: (Long, String) -> Unit,
    val onAddBlock: (InputBlockType) -> Unit,
    val onRemoveBlock: (Long) -> Unit,
    /** 툴바의 설정 버튼. 시트는 화면이 열고 닫는다. */
    val onOpenSettings: () -> Unit,
    val onSend: () -> Unit,
    val onSendRandomSuggestion: () -> Unit,
    /** 잠긴 입력창을 눌렀다. 왜 입력할 수 없는지 알리는 데 쓴다. */
    val onLockedTap: () -> Unit,
)
