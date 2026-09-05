package app.manyak.feature.create

import app.manyak.common.domain.chat.ChatRepository
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.ChatDetail
import app.manyak.common.entity.chat.ChatStreamEvent
import app.manyak.common.entity.chat.ChatSummary
import app.manyak.common.entity.chat.CreatedChat
import app.manyak.common.entity.chat.UserSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.yield

/** 생성 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeChatRepository : ChatRepository {
    val createChatStoryIds = mutableListOf<String>()
    val queuedCreateChatResults = ArrayDeque<DomainResult<CreatedChat>>()

    override suspend fun createChat(
        storyId: String,
        startSettingId: String?,
    ): DomainResult<CreatedChat> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        createChatStoryIds += storyId
        return queuedCreateChatResults.removeFirstOrNull() ?: DomainResult.Success(CreatedChat(id = "chat-1"))
    }

    /** 퍼널은 채팅 목록을 조회하지 않는다 — 계약을 채우기만 한다. */
    override suspend fun myChats(): DomainResult<List<ChatSummary>> = DomainResult.Success(emptyList())

    override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> {
        yield()
        return DomainResult.Success(
            ChatDetail(
                id = chatId,
                storyId = "story-1",
                storyTitle = "",
                prologue = "",
                turns = emptyList(),
                suggestedInputs = emptyList(),
            ),
        )
    }

    /** 퍼널은 턴을 진행하지 않는다 — 계약을 채우기만 한다. */
    override fun streamTurn(
        chatId: String,
        userInput: String,
        userSource: UserSource,
        sourceTurnId: Long?,
        choiceOrder: Int?,
    ): Flow<ChatStreamEvent> = emptyFlow()

    override fun regenerateTurn(
        chatId: String,
        turnId: Long,
    ): Flow<ChatStreamEvent> = emptyFlow()

    override suspend fun generateChoices(
        chatId: String,
        turnId: Long,
    ): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun deleteChat(chatId: String): DomainResult<Unit> = DomainResult.Success(Unit)
}
