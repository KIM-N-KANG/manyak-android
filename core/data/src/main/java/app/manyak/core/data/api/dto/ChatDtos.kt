package app.manyak.core.data.api.dto

import app.manyak.core.domain.chat.ChatDetail
import app.manyak.core.domain.chat.ChatSummary
import app.manyak.core.domain.chat.ChatTurn
import app.manyak.core.domain.chat.CreatedChat
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** [startSettingId] 가 null 이면 직렬화에서 빠지고 서버가 첫 시작 설정으로 폴백한다. */
@Serializable
data class ChatCreateRequestDto(
    val storyId: String,
    val startSettingId: String? = null,
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
    val storyTitle: String = "",
    val thumbnailUrlSm: String? = null,
    val lastStoryPreview: String = "",
    val turnCount: Long = 0,
    val updatedAt: String? = null,
)

fun ChatSummaryDto.toDomain(): ChatSummary =
    ChatSummary(
        id = id,
        storyTitle = storyTitle,
        thumbnailUrl = thumbnailUrlSm?.takeIf { url -> url.isNotBlank() },
        // 빈 미리보기는 완료 턴이 없는 채팅의 정상 값이라 걸러 내지 않는다 — 카드가 안내 문구로 대신한다.
        lastStoryPreview = lastStoryPreview,
        turnCount = turnCount,
        updatedAtEpochMillis = updatedAt?.toEpochMillisOrNull(),
    )

/**
 * ISO 8601 시각을 epoch millis 로 바꾼다. 카드가 상대 시간을 그리려면 날짜 문자열이 아니라 시각 값이
 * 필요하고, 그 값에서 "며칠 전"을 세는 일은 기기 시간대를 아는 화면이 맡는다.
 *
 * `java.time` 을 쓰려고 코어 라이브러리 디슈가링을 켜지 않는다(minSdk 24) — 스토리 상세의 생성일
 * 처리와 같은 판단이다. 서버 계약은 UTC 지만 오프셋이 붙어 와도 맞게 읽는다. 오프셋을 무시하면
 * 시간대가 바뀐 날 방금 진행한 채팅이 "9시간 전"으로 보이는 식으로 조용히 어긋난다.
 *
 * 형식이 예상과 다르면 null 을 돌려준다 — 카드가 시각 자리를 그리지 않는다.
 */
private fun String.toEpochMillisOrNull(): Long? {
    val match = InstantPattern.matchEntire(trim()) ?: return null
    val (dateTime, fraction, zone) = match.destructured
    // 정규식이 자릿수를 이미 강제하므로 숫자 변환은 실패하지 않는다.
    val fields = dateTime.split('-', 'T', 't', ' ', ':').map(String::toInt)
    val (year, month, day) = fields
    val (hour, minute, second) = fields.drop(DATE_FIELD_COUNT)

    // 관대 모드에서는 13월·32일이 조용히 다음 달로 넘어가, 잘못된 값이 그럴듯한 시각이 된다.
    val calendar = GregorianCalendar(UtcTimeZone).apply { isLenient = false }
    calendar.clear()
    return runCatching {
        calendar.set(year, month - 1, day, hour, minute, second)
        // 소수점 이하 자릿수는 서버 구성에 따라 달라지므로 밀리초 세 자리로 맞춘다.
        calendar.set(Calendar.MILLISECOND, fraction.padEnd(MILLIS_DIGITS, '0').take(MILLIS_DIGITS).toInt())
        calendar.timeInMillis - zone.toZoneOffsetMillis()
    }.getOrNull()
}

/** 빈 문자열은 오프셋 표기가 없는 경우이고, 서버 계약대로 UTC 로 읽는다. */
private fun String.toZoneOffsetMillis(): Long {
    if (isEmpty() || equals("Z", ignoreCase = true)) return 0
    val digits = drop(1).replace(":", "")
    val hours = digits.take(2).toLong()
    val minutes = digits.drop(2).ifEmpty { "0" }.toLong()
    val magnitude = hours * MILLIS_PER_HOUR + minutes * MILLIS_PER_MINUTE
    return if (first() == '-') -magnitude else magnitude
}

private val InstantPattern =
    Regex("""^(\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|z|[+-]\d{2}:?\d{2})?$""")

private val UtcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

/** 쪼갠 여섯 칸 중 앞의 셋이 날짜, 뒤의 셋이 시각이다. */
private const val DATE_FIELD_COUNT = 3

private const val MILLIS_DIGITS = 3
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L

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
