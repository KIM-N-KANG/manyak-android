package app.manyak.chat.entity

/** 채팅 입력 모드. 기본은 [BLOCK] 이다. */
enum class ChatInputMode {
    /** 상황과 대사를 나눠 적는 블럭 입력. */
    BLOCK,

    /** 한 입력창에 자유롭게 적는 일반 입력. */
    PLAIN,
}
