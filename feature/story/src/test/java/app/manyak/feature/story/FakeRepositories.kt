package app.manyak.feature.story

import app.manyak.common.domain.chat.ChatRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.StoryRepository
import app.manyak.common.entity.chat.ChatDetail
import app.manyak.common.entity.chat.ChatStreamEvent
import app.manyak.common.entity.chat.ChatSummary
import app.manyak.common.entity.chat.CreatedChat
import app.manyak.common.entity.chat.UserSource
import app.manyak.common.entity.story.StoryDetail
import app.manyak.common.entity.story.StoryStartSetting
import app.manyak.common.entity.story.StorySummary
import app.manyak.report.domain.ReportRepository
import app.manyak.report.entity.StoryReportReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.yield

internal const val STORY_ID = "story-1"

internal fun sampleStoryDetail(
    turnCount: Long = 128,
    startSettings: List<StoryStartSetting> = sampleStartSettings(),
    reachedEndings: List<String> = emptyList(),
    isOwner: Boolean = true,
): StoryDetail =
    StoryDetail(
        id = STORY_ID,
        title = "두 번째 시계공",
        oneLineIntro = "멈춘 시계탑을 고치는 견습공의 하루",
        authorNickname = "마냑",
        description = "도시의 모든 시계가 같은 시각에 멈췄다.",
        genres = listOf("판타지", "미스터리"),
        thumbnailUrl = "https://cdn.manyak.app/thumbnails/1.png",
        turnCount = turnCount,
        createdDate = "2026-08-27",
        startSettings = startSettings,
        reachedEndings = reachedEndings,
        characters = emptyList(),
        isOwner = isOwner,
    )

internal fun sampleStartSettings(): List<StoryStartSetting> =
    listOf(
        StoryStartSetting(
            id = "setting-a",
            name = "폐허가 된 시계탑 앞",
            startSituation = "문은 안에서 잠겨 있다.",
            endings = listOf("시계탑의 아침"),
        ),
        StoryStartSetting(
            id = "setting-b",
            name = "시계공의 작업실",
            startSituation = "낯선 열쇠가 놓여 있다.",
            endings = emptyList(),
        ),
    )

/** 조회 결과는 큐에서 꺼내고 비면 성공 샘플을 돌려준다. */
internal class FakeStoryRepository :
    StoryRepository,
    ReportRepository {
    var storyDetailCallCount = 0
    val queuedDetailResults = ArrayDeque<DomainResult<StoryDetail>>()

    /** 채우면 조회가 여기서 멈춘다 — 조회가 진행 중인 동안의 동작을 볼 때 쓴다. */
    var inFlightGate: CompletableDeferred<Unit>? = null

    override suspend fun storyDetail(storyId: String): DomainResult<StoryDetail> {
        // 실제 네트워크 호출처럼 반드시 한 번 양보한다.
        yield()
        storyDetailCallCount++
        inFlightGate?.await()
        return queuedDetailResults.removeFirstOrNull() ?: DomainResult.Success(sampleStoryDetail())
    }

    override suspend fun originalStories(): DomainResult<List<StorySummary>> = DomainResult.Success(emptyList())

    override suspend fun myStories(): DomainResult<List<StorySummary>> = DomainResult.Success(emptyList())

    val deletedStoryIds = mutableListOf<String>()
    val queuedDeleteResults = ArrayDeque<DomainResult<Unit>>()

    override suspend fun deleteStory(storyId: String): DomainResult<Unit> {
        yield()
        deletedStoryIds += storyId
        return queuedDeleteResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }

    /** 접수된 신고 — 화면이 무엇을 보냈는지 확인할 때 쓴다. */
    val reportedStories = mutableListOf<Triple<String, StoryReportReason, String?>>()
    val queuedReportResults = ArrayDeque<DomainResult<Unit>>()

    override suspend fun reportStory(
        storyId: String,
        reason: StoryReportReason,
        detail: String?,
    ): DomainResult<Unit> {
        yield()
        reportedStories += Triple(storyId, reason, detail)
        return queuedReportResults.removeFirstOrNull() ?: DomainResult.Success(Unit)
    }
}

internal class FakeChatRepository : ChatRepository {
    /** 호출마다 넘어온 시작 설정 — null 이면 서버 폴백을 쓴 호출이다. */
    val createChatStartSettingIds = mutableListOf<String?>()
    val queuedCreateChatResults = ArrayDeque<DomainResult<CreatedChat>>()

    override suspend fun createChat(
        storyId: String,
        startSettingId: String?,
    ): DomainResult<CreatedChat> {
        yield()
        createChatStartSettingIds += startSettingId
        return queuedCreateChatResults.removeFirstOrNull() ?: DomainResult.Success(CreatedChat(id = "chat-1"))
    }

    /** 상세는 채팅 목록을 조회하지 않는다 — 계약을 채우기만 한다. */
    override suspend fun myChats(): DomainResult<List<ChatSummary>> = DomainResult.Success(emptyList())

    override suspend fun chatDetail(chatId: String): DomainResult<ChatDetail> =
        DomainResult.Failure(DomainError.Unknown)

    /** 상세는 턴을 진행하지 않는다 — 계약을 채우기만 한다. */
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
