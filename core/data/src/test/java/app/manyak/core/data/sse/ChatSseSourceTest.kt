package app.manyak.core.data.sse

import app.manyak.core.data.api.dto.ChatTurnStreamRequestDto
import app.manyak.core.data.di.DataLayerConfig
import app.manyak.core.domain.chat.ChatStreamEvent
import app.manyak.core.domain.error.DomainError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSseSourceTest {
    @Test
    fun `완료 사건이 오면 스트림이 끝난다`() =
        runTest {
            val factory = FakeEventSourceFactory()
            val source = source(factory)
            val events = mutableListOf<ChatStreamEvent>()

            val job = launch(dispatcher()) { source.streamTurn(CHAT_ID, request()).toList(events) }
            factory.emit("token", """{"text":"문이"}""")
            factory.emit("token", """{"text":" 열린다"}""")
            factory.emit("completed", "{}")
            job.join()

            assertEquals(
                listOf(
                    ChatStreamEvent.Token("문이"),
                    ChatStreamEvent.Token(" 열린다"),
                    ChatStreamEvent.Completed,
                ),
                events,
            )
        }

    @Test
    fun `종단 사건 없이 끝나면 중단으로 올린다`() =
        runTest {
            val factory = FakeEventSourceFactory()
            val source = source(factory)
            val events = mutableListOf<ChatStreamEvent>()

            val job = launch(dispatcher()) { source.streamTurn(CHAT_ID, request()).toList(events) }
            factory.emit("token", """{"text":"문이"}""")
            factory.listener.onClosed(factory.eventSource)
            job.join()

            assertEquals(listOf(ChatStreamEvent.Token("문이"), ChatStreamEvent.Interrupted), events)
        }

    @Test
    fun `완료 뒤에 닫혀도 중단을 덧붙이지 않는다`() =
        runTest {
            val factory = FakeEventSourceFactory()
            val source = source(factory)
            val events = mutableListOf<ChatStreamEvent>()

            val job = launch(dispatcher()) { source.streamTurn(CHAT_ID, request()).toList(events) }
            factory.emit("completed", "{}")
            factory.listener.onClosed(factory.eventSource)
            job.join()

            assertEquals(listOf(ChatStreamEvent.Completed), events)
        }

    @Test
    fun `스트림을 열지 못하면 상태를 실은 실패로 올린다`() =
        runTest {
            val factory = FakeEventSourceFactory()
            val source = source(factory)
            val events = mutableListOf<ChatStreamEvent>()

            val job = launch(dispatcher()) { source.streamTurn(CHAT_ID, request()).toList(events) }
            factory.listener.onFailure(factory.eventSource, null, response(HTTP_PAYMENT_REQUIRED))
            job.join()

            val failure = events.single() as ChatStreamEvent.Failed
            assertEquals(
                DomainError.Server(status = HTTP_PAYMENT_REQUIRED, code = null, requestId = null),
                failure.error,
            )
        }

    @Test
    fun `수집을 취소하면 스트림을 끊고 실패를 흘리지 않는다`() =
        runTest {
            // 취소가 실패로 둔갑하면 방을 나갈 때마다 실패 안내가 뜬다.
            val factory = FakeEventSourceFactory()
            val source = source(factory)
            val events = mutableListOf<ChatStreamEvent>()

            val job = launch(dispatcher()) { source.streamTurn(CHAT_ID, request()).collect { events += it } }
            factory.emit("token", """{"text":"문이"}""")
            job.cancelAndJoin()

            assertTrue(factory.cancelled)

            factory.listener.onFailure(factory.eventSource, IOException("Canceled"), null)

            assertEquals(listOf(ChatStreamEvent.Token("문이")), events)
            assertFalse(events.any { event -> event is ChatStreamEvent.Failed })
        }

    @Test
    fun `요청 경로와 본문이 서버 계약대로 만들어진다`() =
        runTest {
            val factory = FakeEventSourceFactory()
            val source = source(factory)

            val job =
                launch(dispatcher()) {
                    source
                        .streamTurn(
                            CHAT_ID,
                            ChatTurnStreamRequestDto(
                                userInput = "문을 연다",
                                userSource = "choice",
                                sourceTurnId = 7,
                                choiceOrder = 2,
                            ),
                        ).collect { }
                }
            val request = factory.request

            assertEquals("https://example.com/api/v1/chats/chat-1/turns/stream", request.url.toString())
            assertEquals("text/event-stream, application/json", request.header("Accept"))
            assertEquals(
                """{"userInput":"문을 연다","userSource":"choice","sourceTurnId":7,"choiceOrder":2}""",
                request.bodyText(),
            )

            job.cancelAndJoin()
        }

    @Test
    fun `보내지 않는 선택 정보는 본문에서 빠진다`() =
        runTest {
            val factory = FakeEventSourceFactory()
            val source = source(factory)

            val job = launch(dispatcher()) { source.streamTurn(CHAT_ID, request()).collect { } }

            assertEquals("""{"userInput":"문을 연다","userSource":"typed"}""", factory.request.bodyText())

            job.cancelAndJoin()
        }

    private fun kotlinx.coroutines.test.TestScope.dispatcher() = UnconfinedTestDispatcher(testScheduler)

    private fun kotlinx.coroutines.test.TestScope.source(factory: FakeEventSourceFactory) =
        ChatSseSource(
            eventSourceFactory = factory,
            config =
                DataLayerConfig(
                    apiBaseUrl = "https://example.com/api/v1/",
                    isDebugBuild = true,
                    appVersion = "1.0",
                ),
            json = Json { explicitNulls = false },
            ioDispatcher = dispatcher(),
        )

    private fun request() = ChatTurnStreamRequestDto(userInput = "문을 연다", userSource = "typed")

    private fun Request.bodyText(): String = Buffer().also { buffer -> body?.writeTo(buffer) }.readUtf8()

    private fun response(code: Int): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .build()

    private companion object {
        const val CHAT_ID = "chat-1"
        const val HTTP_PAYMENT_REQUIRED = 402
    }
}

/** 리스너를 붙잡아 테스트가 스트림을 직접 몰 수 있게 한다. */
private class FakeEventSourceFactory : EventSource.Factory {
    lateinit var listener: EventSourceListener
    lateinit var request: Request
    var cancelled = false
        private set

    val eventSource =
        object : EventSource {
            override fun request(): Request = this@FakeEventSourceFactory.request

            override fun cancel() {
                cancelled = true
            }
        }

    override fun newEventSource(
        request: Request,
        listener: EventSourceListener,
    ): EventSource {
        this.request = request
        this.listener = listener
        return eventSource
    }

    fun emit(
        type: String,
        data: String,
    ) {
        listener.onEvent(eventSource, null, type, data)
    }
}
