package app.manyak.feature.chat

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatRepository
import app.manyak.core.domain.chat.ChatStreamEvent
import app.manyak.core.domain.chat.ChatSummary
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.chat.CreatedChat
import app.manyak.core.domain.chat.UserSource
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

/** 서버가 최근 활동순으로 준 목록. 순서를 그대로 지키는지 보려고 제목을 시각 순서와 어긋나게 둔다. */
internal fun sampleChats(): List<ChatSummary> =
    listOf(
        ChatSummary(
            id = "chat-1",
            storyTitle = "두 번째 시계공",
            thumbnailUrl = null,
            lastStoryPreview = "문이 열리자 태엽 소리가 쏟아진다.",
            turnCount = 21,
            updatedAtEpochMillis = 1_756_000_000_000L,
        ),
        ChatSummary(
            id = "chat-2",
            storyTitle = "달빛 아래의 계약",
            thumbnailUrl = null,
            lastStoryPreview = "",
            turnCount = 0,
            updatedAtEpochMillis = 1_755_000_000_000L,
        ),
    )

/** 조회 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeChatRepository : ChatRepository {
    val chatDetailIds = mutableListOf<String>()
    val queuedChatDetailResults = ArrayDeque<DomainResult<ChatDetail>>()
    val queuedMyChatsResults = ArrayDeque<DomainResult<List<ChatSummary>>>()

    var myChatsCallCount = 0
        private set

    /** 조회가 응답 전에 멈춰 있어야 하는 테스트가 채운다 — 취소되면 여기서 던진다. */
    var myChatsGate: CompletableDeferred<Unit>? = null

    override suspend fun myChats(): DomainResult<List<ChatSummary>> {
        yield()
        myChatsCallCount++
        myChatsGate?.await()
        return queuedMyChatsResults.removeFirstOrNull() ?: DomainResult.Success(sampleChats())
    }

    override suspend fun createChat(
        storyId: String,
        startSettingId: String?,
    ): DomainResult<CreatedChat> {
        yield()
        return DomainResult.Success(CreatedChat(id = "chat-1"))
    }

    override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        chatDetailIds += chatId
        return queuedChatDetailResults.removeFirstOrNull() ?: DomainResult.Success(sampleChatDetail(chatId))
    }

    // 아래 넷은 턴 진행·선택지·삭제가 화면에 붙을 때 채운다. 지금 화면은 부르지 않는다.

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
    ): DomainResult<Unit> {
        yield()
        return DomainResult.Success(Unit)
    }

    override suspend fun deleteChat(chatId: String): DomainResult<Unit> {
        yield()
        return DomainResult.Success(Unit)
    }
}
