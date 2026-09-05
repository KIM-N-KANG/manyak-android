package app.manyak.core.data.sse

import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.entity.chat.ChatStreamEvent
import app.manyak.core.data.api.HEADER_REQUEST_ID
import app.manyak.core.data.api.dto.ChatRegenerateRequestDto
import app.manyak.core.data.api.dto.ChatTurnStreamRequestDto
import app.manyak.core.data.di.DataLayerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅 턴 SSE 스트림. 요청 본문을 실은 POST 의 응답을 사건 단위로 흘린다.
 *
 * Retrofit 이 아니라 OkHttp 를 직접 쓰는 이유는 응답이 한 번에 오는 본문이 아니기 때문이다. 프레이밍은
 * okhttp-sse 가 맡고, 이 클래스는 수명과 종단 판정만 책임진다.
 */
@Singleton
class ChatSseSource
    @Inject
    constructor(
        private val eventSourceFactory: EventSource.Factory,
        private val config: DataLayerConfig,
        private val json: Json,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        fun streamTurn(
            chatId: String,
            request: ChatTurnStreamRequestDto,
        ): Flow<ChatStreamEvent> =
            stream(
                listOf(PATH_CHATS, chatId, PATH_TURNS, PATH_STREAM),
                json.encodeToString(request),
            )

        fun regenerateTurn(
            chatId: String,
            request: ChatRegenerateRequestDto,
        ): Flow<ChatStreamEvent> =
            stream(
                listOf(PATH_CHATS, chatId, PATH_TURNS, PATH_REGENERATE, PATH_STREAM),
                json.encodeToString(request),
            )

        /**
         * 구독이 곧 요청이라 **cold 다.** 수집을 취소하면 [awaitClose] 가 스트림을 끊는다.
         *
         * 사건은 [trySendBlocking] 으로 보낸다. 소비가 밀리면 OkHttp 읽기 스레드가 함께 멈춰 소켓
         * 읽기가 느려지는 것이 옳은 역압이고, 버퍼가 찼다고 토큰을 버리면 본문에 구멍이 난다.
         */
        private fun stream(
            pathSegments: List<String>,
            requestBody: String,
        ): Flow<ChatStreamEvent> =
            callbackFlow {
                // 종단 사건을 이미 흘렸는지, 또는 취소로 끝나는 중인지 나타낸다. 취소가 부르는
                // onFailure 를 실패로 만들지 않기 위한 표시다.
                val terminated = AtomicBoolean(false)

                val listener =
                    object : EventSourceListener() {
                        override fun onEvent(
                            eventSource: EventSource,
                            id: String?,
                            type: String?,
                            data: String,
                        ) {
                            val event = chatStreamEventOf(type, data) ?: return
                            trySendBlocking(event)
                            if (event.isTerminal()) {
                                terminated.set(true)
                                close()
                            }
                        }

                        override fun onClosed(eventSource: EventSource) {
                            if (!terminated.getAndSet(true)) {
                                trySendBlocking(ChatStreamEvent.Interrupted)
                            }
                            close()
                        }

                        override fun onFailure(
                            eventSource: EventSource,
                            t: Throwable?,
                            response: Response?,
                        ) {
                            if (terminated.getAndSet(true)) {
                                close()
                                return
                            }
                            val error = sseDomainError(t, response?.code, response?.header(HEADER_REQUEST_ID))
                            trySendBlocking(ChatStreamEvent.Failed(error))
                            close()
                        }
                    }

                val eventSource = eventSourceFactory.newEventSource(request(pathSegments, requestBody), listener)

                awaitClose {
                    // cancel() 은 onFailure 를 부른다. 먼저 표시를 세워 이탈이 실패 안내로 보이지 않게 한다.
                    terminated.set(true)
                    eventSource.cancel()
                }
            }.flowOn(ioDispatcher)

        private fun request(
            pathSegments: List<String>,
            requestBody: String,
        ): Request =
            Request
                .Builder()
                .url(url(pathSegments))
                .post(requestBody.toRequestBody(JsonMediaType))
                // 성공은 SSE 지만 402·409 같은 실패는 동기 JSON 으로 온다. 둘 다 받는다고 알려야
                // 서버가 오류를 스트림이 아니라 응답으로 낸다.
                .header(HEADER_ACCEPT, ACCEPT_EVENT_STREAM_OR_JSON)
                .build()

        private fun url(pathSegments: List<String>): HttpUrl =
            config.apiBaseUrl
                .toHttpUrl()
                .newBuilder()
                .apply { pathSegments.forEach(::addPathSegment) }
                .build()

        private fun ChatStreamEvent.isTerminal(): Boolean =
            this is ChatStreamEvent.Completed || this is ChatStreamEvent.Failed

        private companion object {
            val JsonMediaType = "application/json".toMediaType()

            const val HEADER_ACCEPT = "Accept"
            const val ACCEPT_EVENT_STREAM_OR_JSON = "text/event-stream, application/json"

            const val PATH_CHATS = "chats"
            const val PATH_TURNS = "turns"
            const val PATH_STREAM = "stream"
            const val PATH_REGENERATE = "regenerate"
        }
    }
