package app.manyak.feature.chat.suggestion

import app.manyak.common.entity.chat.UserSource
import app.manyak.feature.chat.ChatRoomTurn
import app.manyak.feature.chat.composer.BLOCK_SEND_SEPARATOR
import app.manyak.feature.chat.composer.parseInputBlocks
import app.manyak.feature.chat.composer.serializeInputBlocks
import kotlin.random.Random

/**
 * 화면에 그릴 추천 목록과 그 출처.
 *
 * [sourceTurnId] 가 null 이면 원본 턴이 없는 시작 추천이고, 그때는 서버에 선택 메타데이터를 싣지 않는다.
 */
data class ChatSuggestions(
    val items: List<String> = emptyList(),
    val sourceTurnId: Long? = null,
) {
    /** 무작위로 보낼 것이 있는지. 공백만 있는 항목은 후보가 아니다. */
    val hasCandidate: Boolean
        get() = items.any { item -> item.isNotBlank() }
}

/**
 * 추천 목록의 소스는 **턴 수가 가른다** — 턴이 0개면 상세 응답의 시작 추천, 턴이 있으면 마지막 턴의
 * 선택지다.
 *
 * 추천 토글은 선택지만 가린다. 시작 추천은 토글과 무관하게 그리고, 대신 무작위 전송 버튼이
 * 토글 상태를 따른다.
 */
fun chatSuggestions(
    lastTurn: ChatRoomTurn?,
    suggestedInputs: List<String>,
    choicesEnabled: Boolean,
): ChatSuggestions =
    when {
        lastTurn == null -> ChatSuggestions(items = suggestedInputs)
        choicesEnabled -> ChatSuggestions(items = lastTurn.choices, sourceTurnId = lastTurn.id)
        else -> ChatSuggestions()
    }

/**
 * 채우기로 입력창에 넣어 둔 추천 원문.
 *
 * 전송 시점에 지금 문장과 대조해 출처를 가르고, 전송에 성공하면 비운다 — 다음 턴의 입력이 앞 턴에서
 * 채운 문장과 대조되면 안 된다.
 */
data class FilledSuggestion(
    val text: String,
    val position: Int,
    val sourceTurnId: Long?,
)

/** 서버에 실어 보낼 출처. */
data class SuggestionOrigin(
    val userSource: UserSource,
    val sourceTurnId: Long? = null,
    val choiceOrder: Int? = null,
)

/**
 * 컴포저로 보낸 문장의 출처.
 *
 * 서버는 문자열만으로 "추천과 같은 문장을 사용자가 직접 썼다"를 가릴 수 없어 입력 방식을 아는
 * 클라이언트가 정한다. 채운 적이 없으면 직접 입력이고, 채운 원문을 그대로 보냈으면 선택,
 * 손대서 달라졌으면 고쳐 보낸 선택이다.
 */
fun composerOrigin(
    userInput: String,
    filled: FilledSuggestion?,
): SuggestionOrigin {
    if (filled == null) return SuggestionOrigin(UserSource.TYPED)
    val userSource = if (filled.matches(userInput)) UserSource.CHOICE else UserSource.EDITED_CHOICE
    return originOf(userSource, position = filled.position, sourceTurnId = filled.sourceTurnId)
}

/** 선택지를 눌러 바로 보낸 문장의 출처. 입력창을 거치지 않아 대조할 것이 없다. */
fun choiceOrigin(
    position: Int,
    sourceTurnId: Long?,
): SuggestionOrigin = originOf(UserSource.CHOICE, position = position, sourceTurnId = sourceTurnId)

/**
 * 즉시 전송도 블럭 전송과 같은 모양으로 정규화한다 — 그래야 `*상황*` 이 섞인 추천을 눌렀을 때와
 * 같은 문장을 직접 입력했을 때의 저장 본문이 갈리지 않는다.
 */
fun normalizeSuggestion(text: String): String = serializeInputBlocks(parseInputBlocks(text), BLOCK_SEND_SEPARATOR)

/**
 * 공백이 아닌 후보 중 한 자리를 균등 무작위로 고른다.
 *
 * 돌려주는 값은 **원래 목록에서의 자리**다 — 후보만 추린 목록의 순번을 보내면 서버가 기록하는
 * 선택 위치가 화면과 어긋난다.
 */
fun randomSuggestionPosition(
    items: List<String>,
    random: Random,
): Int? {
    val candidates = items.indices.filter { position -> items[position].isNotBlank() }
    if (candidates.isEmpty()) return null
    return candidates[random.nextInt(candidates.size)]
}

/** 선택지 생성의 진행 상태. **대상 턴에 묶어** 새 턴이 시작되면 낡은 상태가 화면에서 자연히 빠진다. */
data class ChoicesProgress(
    val turnId: Long,
    val failed: Boolean = false,
)

/**
 * 선택지 생성 요청을 지금 보내야 하는지. 토글이 켜져 있고, 진행 중이 아니며, 마지막 턴에 아직
 * 선택지가 없을 때만 보낸다.
 */
fun shouldGenerateChoices(
    lastTurn: ChatRoomTurn?,
    choicesEnabled: Boolean,
    isStreaming: Boolean,
): Boolean = choicesEnabled && !isStreaming && lastTurn != null && lastTurn.choices.isEmpty()

/**
 * 채운 원문을 그대로 보냈는지.
 *
 * 블럭 모드로 채우면 쪼갰다 다시 이어지므로 **그 왕복 결과도 그대로 보낸 것으로 인정한다** —
 * 손대지 않았는데 모드 때문에 고쳐 보낸 것으로 기록되면 안 된다.
 */
private fun FilledSuggestion.matches(userInput: String): Boolean {
    val submitted = userInput.trim()
    return submitted == text.trim() || submitted == normalizeSuggestion(text).trim()
}

/** 원본 턴이 없으면 순번도 뜻이 없다. 한쪽만 싣지 않는다. */
private fun originOf(
    userSource: UserSource,
    position: Int,
    sourceTurnId: Long?,
): SuggestionOrigin =
    if (sourceTurnId == null) {
        SuggestionOrigin(userSource)
    } else {
        SuggestionOrigin(userSource, sourceTurnId = sourceTurnId, choiceOrder = position + CHOICE_ORDER_BASE)
    }

/** 화면 위치는 0부터, 서버 순번은 1부터다. */
private const val CHOICE_ORDER_BASE = 1
