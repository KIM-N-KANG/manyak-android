package app.manyak.core.data.api.dto

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatSummary
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.chat.CreatedChat
import kotlinx.serialization.Serializable

/** [startSettingId] 가 null 이면 직렬화에서 빠지고 서버가 첫 시작 설정으로 폴백한다. */
@Serializable
data class ChatCreateRequestDto(
    val storyId: String,
    val startSettingId: String? = null,
)

/**
 * 턴 진행 요청. `null` 필드는 직렬화에서 빠진다(`explicitNulls = false`).
 *
 * [sourceTurnId]·[choiceOrder] 는 고른 선택지를 서버가 기록하는 데만 쓰이고, 값이 낡았거나 범위를
 * 벗어나도 서버가 거절하지 않고 기록만 생략한다.
 */
@Serializable
data class ChatTurnStreamRequestDto(
    val userInput: String,
    val userSource: String? = null,
    val sourceTurnId: Long? = null,
    val choiceOrder: Int? = null,
)

/** 재생성 요청. 서버가 보는 마지막 턴과 다르면 409 다. */
@Serializable
data class ChatRegenerateRequestDto(
    val turnId: Long,
)

/**
 * 선택지 생성 응답. **렌더 소스가 아니다** — 화면은 상세 재조회의 `turns[].choices` 로 그린다.
 * 본문을 무시하지 않고 받아 두는 이유는 성공 응답에 본문이 없으면 역직렬화 실패로 판정되기 때문이다.
 */
@Serializable
data class ChatChoicesResponseDto(
    val choices: List<String> = emptyList(),
)

/** 응답의 프롤로그·추천 입력은 채팅방 진입 시 상세 조회로 다시 얻으므로 식별자만 역직렬화한다. */
@Serializable
data class ChatCreateResponseDto(
    val id: String,
)

fun ChatCreateResponseDto.toDomain(): CreatedChat = CreatedChat(id = id)

/**
 * 채팅 목록 한 건. 카드가 쓰지 않는 참조 스토리 ID·도달 엔딩은 역직렬화하지 않는다.
 *
 * 식별자 밖의 필드에 기본값을 두는 이유는 스토리 목록과 같다 — 서버가 필드를 하나 빼도 목록 전체가
 * 실패로 떨어지지 않게 한다. **웹처럼 필드가 빠진 항목을 목록에서 걸러 내지는 않는다.** 그 필터는
 * 서버 계약이 아니라 생성기가 응답 전 필드를 옵셔널로 뽑은 데 대한 방어이고, 앱이 같은 필터를 두면
 * 계약이 깨졌을 때 그 사실이 오류가 아니라 조용히 짧아진 목록으로 나타난다.
 */
@Serializable
data class ChatSummaryDto(
    val id: String,
    val storyId: String = "",
    val storyTitle: String = "",
    val thumbnailUrlSm: String? = null,
    val lastStoryPreview: String = "",
    val turnCount: Long = 0,
    val updatedAt: String? = null,
)

fun ChatSummaryDto.toDomain(): ChatSummary =
    ChatSummary(
        id = id,
        storyId = storyId,
        storyTitle = storyTitle,
        thumbnailUrl = thumbnailUrlSm?.takeIf { url -> url.isNotBlank() },
        // 빈 미리보기는 완료 턴이 없는 채팅의 정상 값이라 걸러 내지 않는다 — 카드가 안내 문구로 대신한다.
        lastStoryPreview = lastStoryPreview,
        turnCount = turnCount,
        updatedAtEpochMillis = updatedAt?.toEpochMillisOrNull(),
    )

@Serializable
data class ChatDetailResponseDto(
    val id: String,
    val storyId: String,
    val storyTitle: String = "",
    val prologue: String = "",
    val turns: List<ChatTurnDto> = emptyList(),
    val suggestedInputs: List<String> = emptyList(),
)

@Serializable
data class ChatTurnDto(
    val id: Long,
    val userInput: String = "",
    val aiOutput: String = "",
    val choices: List<String> = emptyList(),
    val reachedEnding: String? = null,
)

fun ChatDetailResponseDto.toDomain(): ChatDetail =
    ChatDetail(
        id = id,
        storyId = storyId,
        storyTitle = storyTitle,
        prologue = prologue,
        turns =
            turns.map { turn ->
                ChatTurn(
                    id = turn.id,
                    userInput = turn.userInput,
                    aiOutput = turn.aiOutput,
                    choices = turn.choices,
                    reachedEnding = turn.reachedEnding?.takeIf { name -> name.isNotBlank() },
                )
            },
        suggestedInputs = suggestedInputs,
    )
