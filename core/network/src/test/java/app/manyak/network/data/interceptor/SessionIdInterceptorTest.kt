package app.manyak.network.data.interceptor

import app.manyak.network.data.api.HEADER_SESSION_ID
import app.manyak.network.domain.SessionIdAccess
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionIdInterceptorTest {
    @Test
    fun `현재 세션 값을 요청마다 읽고 미초기화이면 오래된 헤더도 제거한다`() {
        var session: Long? = 123L
        val sent = mutableListOf<List<String>>()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(SessionIdInterceptor(SessionIdAccess { session }))
                .addInterceptor { chain ->
                    sent += chain.request().headers(HEADER_SESSION_ID)
                    Response
                        .Builder()
                        .request(
                            chain.request(),
                        ).protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody())
                        .build()
                }.build()
        val request =
            Request
                .Builder()
                .url("https://fixture.invalid/")
                .header(HEADER_SESSION_ID, "stale")
                .build()
        for (value in listOf(123L, 456L, null, -1L)) {
            session = value
            client.newCall(request).execute().close()
        }
        assertEquals(listOf(listOf("123"), listOf("456"), emptyList(), emptyList()), sent)
    }
}
