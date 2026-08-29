package app.manyak.feature.chat

import app.manyak.core.domain.chat.ChatStreamEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 토큰을 [TOKEN_BATCH_MILLIS] 단위로 모아 하나의 [ChatStreamEvent.Token] 으로 넘긴다.
 *
 * 사건 하나마다 상태가 바뀌고 그때마다 화면이 다시 그려진다. 토큰을 그대로 흘리면 초당 수십~수백 번
 * 다시 그리게 되고, 자라는 본문을 매번 다시 파싱하게 된다.
 *
 * **모아 둔 조각이 있을 때만 시간을 잰다** — 주기적으로 도는 타이머를 두면 스트림이 조용한 동안에도
 * 깨어나고, 가상 시간을 쓰는 테스트에서는 영원히 끝나지 않는다.
 *
 * 토큰이 아닌 사건 앞에서는 모아 둔 조각을 **먼저** 흘린다. 순서가 뒤집히면 인물 이미지가 잘못된
 * 문단에 끼어든다.
 */
internal suspend fun Flow<ChatStreamEvent>.collectBatched(onEvent: suspend (ChatStreamEvent) -> Unit) =
    coroutineScope {
        val events = Channel<ChatStreamEvent>()
        launch {
            try {
                collect { event -> events.send(event) }
            } finally {
                events.close()
            }
        }

        val tokens = StringBuilder()
        while (true) {
            val received =
                if (tokens.isEmpty()) {
                    events.receiveCatching()
                } else {
                    withTimeoutOrNull(TOKEN_BATCH_MILLIS) { events.receiveCatching() }
                }
            if (received == null) {
                // 시간이 찼다. 모아 둔 조각을 흘리고 다시 기다린다.
                onEvent(tokens.drain())
            } else {
                val event = received.getOrNull() ?: break
                emitBatched(event, tokens, onEvent)
            }
        }
    }

private suspend fun emitBatched(
    event: ChatStreamEvent,
    tokens: StringBuilder,
    onEvent: suspend (ChatStreamEvent) -> Unit,
) {
    if (event is ChatStreamEvent.Token) {
        tokens.append(event.text)
        return
    }
    if (tokens.isNotEmpty()) onEvent(tokens.drain())
    onEvent(event)
}

/** 모아 둔 조각을 토큰 하나로 꺼내고 버퍼를 비운다. */
private fun StringBuilder.drain(): ChatStreamEvent.Token {
    val text = toString()
    setLength(0)
    return ChatStreamEvent.Token(text)
}

/**
 * 토큰을 모으는 간격. 프레임 하나(약 17ms)보다 크게 잡아 다시 그리는 횟수를 초당 스무 번쯤으로
 * 묶는다. 측정으로 정한 값이 아니라 시작값이다.
 */
private const val TOKEN_BATCH_MILLIS = 50L
