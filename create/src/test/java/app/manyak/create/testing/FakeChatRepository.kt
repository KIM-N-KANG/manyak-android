package app.manyak.create.testing

import app.manyak.common.domain.chat.ChatStarter
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.CreatedChat
import kotlinx.coroutines.yield

/** 생성 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeChatRepository : ChatStarter {
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
}
