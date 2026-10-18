package app.manyak.chat.room.presentation

import app.manyak.chat.entity.ChatStreamEvent
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile

/** 끊긴 생성에 다시 붙는 최대 횟수. 첫 재연결을 포함하며, 간격이 두 배씩 늘어 약 7초 동안 시도한다. */
internal const val MAX_GENERATION_RECONNECTS = 4

/** 다시 붙기에 실패한 뒤 다음 시도까지의 첫 간격. 첫 재연결은 기다리지 않고 이후 두 배씩 늘린다. */
internal const val GENERATION_RECONNECT_DELAY_MILLIS = 1_000L

/** 생성을 끝까지 따라간 결과. */
internal sealed interface GenerationEnd {
    data object Completed : GenerationEnd

    /** 서버가 생성을 실패로 끝냈다. 저장된 턴이 있을 수 있어 상세로 확인한다. */
    data class Failed(
        val message: String?,
    ) : GenerationEnd

    /** 처음 연결이 열리지 못했다. 402, 409, 503 은 서버가 요청을 받지 않은 것이다. */
    data class Rejected(
        val error: DomainError,
    ) : GenerationEnd

    /**
     * 다시 붙어도 종료 사건을 받지 못했다. 저장 여부는 상세로 판단한다.
     * [generationId] 는 따라가던 생성이다. 같은 생성을 곧바로 다시 따라가지 않는 데 쓴다.
     */
    data class Lost(
        val generationId: String?,
    ) : GenerationEnd
}

/**
 * 채팅 생성 하나를 종료 사건까지 따라간다.
 *
 * 서버는 연결이 끊겨도 생성을 계속하므로 종료 사건 없이 끊기면 같은 생성에 다시 붙는다. 아는 생성 ID 로
 * 먼저 붙고, 모르거나 다시 붙기에 실패하면 상세에서 생성 중인 ID 를 다시 찾는다.
 *
 * @param first 처음 연결. `null` 이면 [generationId] 로 재연결부터 시작한다(재진입)
 * @param findGenerationId 상세를 다시 읽어 생성 중인 ID 를 찾는다. 생성이 끝났으면 성공한 `null` 이고,
 *   상세를 받지 못한 실패는 생성이 끝난 것으로 보지 않고 다음 차례에 다시 찾는다
 * @param onEvent 화면에 반영할 사건. [ChatStreamEvent.Started] 는 사건을 처음부터 다시 받는다는 신호다
 */
internal suspend fun followGeneration(
    first: Flow<ChatStreamEvent>?,
    generationId: String?,
    resume: (generationId: String) -> Flow<ChatStreamEvent>,
    findGenerationId: suspend () -> DomainResult<String?>,
    onEvent: suspend (ChatStreamEvent) -> Unit,
): GenerationEnd {
    var knownId = generationId
    var followedId = generationId
    var connection = first
    for (attempt in 0..MAX_GENERATION_RECONNECTS) {
        val read = connection?.readUntilEnd(onEvent)
        val end = read?.end
        // 재연결이 열리지 못한 것(404 스트림 없음, 503 수신 불가, 네트워크)은 다음 차례에 다시 찾는다.
        if (end != null && (attempt == 0 || end !is GenerationEnd.Rejected)) return end
        read?.generationId?.let { id ->
            knownId = id
            followedId = id
        }
        if (attempt == MAX_GENERATION_RECONNECTS) break
        if (attempt > 0) delay(GENERATION_RECONNECT_DELAY_MILLIS shl (attempt - 1))
        connection = null
        val found = knownId?.let { id -> DomainResult.Success(id) } ?: findGenerationId()
        knownId = null
        if (found is DomainResult.Success) {
            val target = found.value ?: return GenerationEnd.Lost(followedId)
            followedId = target
            connection = resume(target)
        }
    }
    return GenerationEnd.Lost(followedId)
}

/** 연결 하나를 읽은 결과. [end] 가 `null` 이면 종료 사건 없이 끊겼다. */
private class ConnectionRead(
    val end: GenerationEnd?,
    /** `started` 가 알려 준 생성 ID. */
    val generationId: String?,
)

/**
 * 연결 하나를 종료 사건까지 읽는다.
 *
 * 서버는 늘 `started` 를 먼저 보내므로, 그 전에 온 실패는 연결이 열리지 못한 것이고 그 뒤의 실패는
 * 서버가 생성을 실패로 끝낸 것이다.
 */
private suspend fun Flow<ChatStreamEvent>.readUntilEnd(onEvent: suspend (ChatStreamEvent) -> Unit): ConnectionRead {
    var started = false
    var generationId: String? = null
    var end: GenerationEnd? = null
    transformWhile { event ->
        emit(event)
        event !is ChatStreamEvent.Completed && event !is ChatStreamEvent.Failed && event != ChatStreamEvent.Interrupted
    }.collectBatched { event ->
        when (event) {
            ChatStreamEvent.Completed -> end = GenerationEnd.Completed

            is ChatStreamEvent.Failed ->
                end = if (started) GenerationEnd.Failed(event.message) else GenerationEnd.Rejected(event.error)

            ChatStreamEvent.Interrupted -> Unit

            else -> {
                if (event is ChatStreamEvent.Started) {
                    started = true
                    generationId = event.generationId ?: generationId
                }
                onEvent(event)
            }
        }
    }
    return ConnectionRead(end, generationId)
}
