package app.manyak.feature.chat

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.chat.CreatedChat
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.yield

internal fun sampleChatDetail(chatId: String = "chat-1"): ChatDetail =
    ChatDetail(
        id = chatId,
        storyId = "story-1",
        storyTitle = "두 번째 시계공",
        prologue = "*낡은 시계탑 아래.* 당신은 문 앞에 선다.",
        turns =
            listOf(
                ChatTurn(id = 1, userInput = "문을 연다.", aiOutput = "문이 열리자 태엽 소리가 쏟아진다."),
            ),
        suggestedInputs = emptyList(),
    )

/** 조회 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeChatRepository : ChatRepository {
    val chatDetailIds = mutableListOf<String>()
    val queuedChatDetailResults = ArrayDeque<DomainResult<ChatDetail>>()

    override suspend fun createChat(storyId: String): DomainResult<CreatedChat> {
        yield()
        return DomainResult.Success(CreatedChat(id = "chat-1"))
    }

    override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        chatDetailIds += chatId
        return queuedChatDetailResults.removeFirstOrNull() ?: DomainResult.Success(sampleChatDetail(chatId))
    }
}
