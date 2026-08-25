package app.manyak.feature.create

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.CreatedChat
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.yield

/** 생성 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeChatRepository : ChatRepository {
    val createChatStoryIds = mutableListOf<String>()
    val queuedCreateChatResults = ArrayDeque<DomainResult<CreatedChat>>()

    override suspend fun createChat(storyId: String): DomainResult<CreatedChat> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        createChatStoryIds += storyId
        return queuedCreateChatResults.removeFirstOrNull() ?: DomainResult.Success(CreatedChat(id = "chat-1"))
    }

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
}
