package app.manyak.chat.testing

import app.manyak.chat.domain.ChatPreferencesRepository
import app.manyak.chat.domain.ChatRepository
import app.manyak.chat.entity.ChatDetail
import app.manyak.chat.entity.ChatInputMode
import app.manyak.chat.entity.ChatStreamEvent
import app.manyak.chat.entity.ChatSummary
import app.manyak.chat.entity.ChatTurn
import app.manyak.chat.entity.UserSource
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.chat.CreatedChat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.yield

internal fun sampleChatDetail(
    chatId: String = "chat-1",
    turns: List<ChatTurn> =
        listOf(
            ChatTurn(id = 1, userInput = "문을 연다.", aiOutput = "문이 열리자 태엽 소리가 쏟아진다."),
        ),
    suggestedInputs: List<String> = emptyList(),
): ChatDetail =
    ChatDetail(
        id = chatId,
        storyId = "story-1",
        storyTitle = "두 번째 시계공",
        prologue = "*낡은 시계탑 아래.* 당신은 문 앞에 선다.",
        turns = turns,
        suggestedInputs = suggestedInputs,
    )

/** 서버가 최근 활동순으로 준 목록. 순서를 그대로 지키는지 보려고 제목을 시각 순서와 어긋나게 둔다. */
internal fun sampleChats(): List<ChatSummary> =
    listOf(
        ChatSummary(
            id = "chat-1",
            storyId = "story-1",
            storyTitle = "두 번째 시계공",
            thumbnailUrl = null,
            lastStoryPreview = "문이 열리자 태엽 소리가 쏟아진다.",
            turnCount = 21,
            updatedAtEpochMillis = 1_756_000_000_000L,
        ),
        ChatSummary(
            id = "chat-2",
            storyId = "story-2",
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

    private var lastChatDetail: ChatDetail? = null

    /**
     * 큐가 비면 **마지막으로 돌려준 값**을 다시 준다. 실제 서버처럼 조회를 거듭해도 같은 값이 나와야
     * 한 번의 진행에 여러 번 조회하는 경로(확정 → 선택지 생성 → 재조회)를 테스트가 그대로 볼 수 있다.
     */
    override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        chatDetailIds += chatId
        val result =
            queuedChatDetailResults.removeFirstOrNull()
                ?: lastChatDetail?.let { detail -> DomainResult.Success(detail) }
                ?: DomainResult.Success(sampleChatDetail(chatId))
        (result as? DomainResult.Success)?.let { success -> lastChatDetail = success.value }
        return result
    }

    /** 테스트가 사건을 하나씩 밀어 넣는 통로. 비어 있으면 스트림을 열지 않은 것과 같다. */
    val streamEvents = Channel<ChatStreamEvent>(Channel.UNLIMITED)
    val streamedInputs = mutableListOf<String>()
    val streamedOrigins = mutableListOf<Triple<UserSource, Long?, Int?>>()

    override fun streamTurn(
        chatId: String,
        userInput: String,
        userSource: UserSource,
        sourceTurnId: Long?,
        choiceOrder: Int?,
    ): Flow<ChatStreamEvent> {
        streamedInputs += userInput
        streamedOrigins += Triple(userSource, sourceTurnId, choiceOrder)
        return streamEvents.receiveAsFlow()
    }

    /** 재생성은 이어쓰기와 다른 통로를 쓴다 — 한 테스트에서 둘을 섞어 보내는 경우가 있다. */
    val regenerateEvents = Channel<ChatStreamEvent>(Channel.UNLIMITED)
    val regeneratedTurnIds = mutableListOf<Long>()

    override fun regenerateTurn(
        chatId: String,
        turnId: Long,
    ): Flow<ChatStreamEvent> {
        regeneratedTurnIds += turnId
        return regenerateEvents.receiveAsFlow()
    }

    val generatedChoiceTurnIds = mutableListOf<Long>()
    val queuedChoicesResults = ArrayDeque<DomainResult<Unit>>()

    /** 생성이 응답 전에 멈춰 있어야 하는 테스트가 채운다. */
    var choicesGate: CompletableDeferred<Unit>? = null

    override suspend fun generateChoices(
        chatId: String,
        turnId: Long,
    ): DomainResult<Unit> {
        yield()
        generatedChoiceTurnIds += turnId
        choicesGate?.await()
        return queuedChoicesResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }

    val deletedChatIds = mutableListOf<String>()
    val queuedDeleteResults = ArrayDeque<DomainResult<Unit>>()

    override suspend fun deleteChat(chatId: String): DomainResult<Unit> {
        yield()
        deletedChatIds += chatId
        return queuedDeleteResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }
}

/** 기기 설정 가짜. 기본값은 저장소 구현과 같다. */
internal class FakeChatPreferencesRepository(
    private var mode: ChatInputMode = ChatInputMode.BLOCK,
    private var choices: Boolean = true,
    private var hintSeen: Boolean = true,
) : ChatPreferencesRepository {
    val savedModes = mutableListOf<ChatInputMode>()
    val savedChoices = mutableListOf<Boolean>()

    override suspend fun inputMode(): ChatInputMode = mode

    override suspend fun setInputMode(mode: ChatInputMode) {
        this.mode = mode
        savedModes += mode
    }

    override suspend fun choicesEnabled(): Boolean = choices

    override suspend fun setChoicesEnabled(enabled: Boolean) {
        choices = enabled
        savedChoices += enabled
    }

    var hintSeenMarkCount = 0
        private set

    override suspend fun isChoicesHintSeen(): Boolean = hintSeen

    override suspend fun markChoicesHintSeen() {
        hintSeen = true
        hintSeenMarkCount++
    }
}
