package app.manyak.core.data.api.dto

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.chat.CreatedChat
import kotlinx.serialization.Serializable

/** 시작 설정은 지정하지 않는다 — 서버가 스토리의 첫 시작 설정으로 폴백한다. */
@Serializable
data class ChatCreateRequestDto(
    val storyId: String,
)

/** 응답의 프롤로그·추천 입력은 채팅방 진입 시 상세 조회로 다시 얻으므로 식별자만 역직렬화한다. */
@Serializable
data class ChatCreateResponseDto(
    val id: String,
)

fun ChatCreateResponseDto.toDomain(): CreatedChat = CreatedChat(id = id)

@Serializable
data class ChatDetailResponseDto(
    val id: String,
    val storyId: String,
    val storyTitle: String = "",
    val prologue: String = "",
    val turns: List<ChatTurnDto> = emptyList(),
    val suggestedInputs: List<String> = emptyList(),
)

/** 턴의 선택지·엔딩 도달은 컴포저·선택지 표시가 붙기 전까지 쓰지 않아 역직렬화하지 않는다. */
@Serializable
data class ChatTurnDto(
    val id: Long,
    val userInput: String = "",
    val aiOutput: String = "",
)

fun ChatDetailResponseDto.toDomain(): ChatDetail =
    ChatDetail(
        id = id,
        storyId = storyId,
        storyTitle = storyTitle,
        prologue = prologue,
        turns = turns.map { turn -> ChatTurn(id = turn.id, userInput = turn.userInput, aiOutput = turn.aiOutput) },
        suggestedInputs = suggestedInputs,
    )
