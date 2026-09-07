package app.manyak.network.data.interceptor

import app.manyak.network.domain.SessionTokenAccess
import app.manyak.network.entity.TokenAccess
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthInterceptorTest {
    @Test
    fun `unavailable token prevents the protected request`() {
        val access = FakeAccess(initial = TokenAccess.NoSession)
        val server = TerminalServer(listOf(200))

        val failure = assertThrows(SessionUnavailableException::class.java) { execute(access, server) }

        assertEquals(TokenAccess.NoSession, failure.access)
        assertEquals(emptyList<String?>(), server.authorizationHeaders)
        assertEquals(emptyList<Long>(), access.refreshGenerations)
    }

    @Test
    fun `unauthorized request retries once using its original generation`() {
        val access = FakeAccess()
        val server = TerminalServer(listOf(401, 200))

        execute(access, server).use { assertEquals(200, it.code) }

        assertEquals(listOf("Bearer initial", "Bearer refreshed"), server.authorizationHeaders)
        assertEquals(listOf(41L), access.refreshGenerations)
    }

    @Test
    fun `unavailable refresh returns the original unauthorized response`() {
        val access = FakeAccess(refreshed = TokenAccess.SessionEnded)
        val server = TerminalServer(listOf(401))

        execute(access, server).use { assertEquals(401, it.code) }

        assertEquals(listOf("Bearer initial"), server.authorizationHeaders)
        assertEquals(listOf(41L), access.refreshGenerations)
    }

    @Test
    fun `a second unauthorized response does not trigger another refresh`() {
        val access = FakeAccess()
        val server = TerminalServer(listOf(401, 401))

        execute(access, server).use { assertEquals(401, it.code) }

        assertEquals(2, server.authorizationHeaders.size)
        assertEquals(listOf(41L), access.refreshGenerations)
    }

    private fun execute(
        access: FakeAccess,
        server: TerminalServer,
    ): Response =
        OkHttpClient
            .Builder()
            .addInterceptor(AuthInterceptor(access))
            .addInterceptor { chain -> server.respond(chain.request()) }
            .build()
            .newCall(Request.Builder().url("https://fixture.invalid/protected").build())
            .execute()

    private class FakeAccess(
        private val initial: TokenAccess = TokenAccess.Available("initial"),
        private val refreshed: TokenAccess = TokenAccess.Available("refreshed"),
    ) : SessionTokenAccess {
        override val currentGeneration: Long = 41L
        val refreshGenerations = mutableListOf<Long>()

        override suspend fun accessToken(): TokenAccess = initial

        override suspend fun refreshAfterUnauthorized(observedGeneration: Long): TokenAccess {
            refreshGenerations += observedGeneration
            return refreshed
        }
    }

    private class TerminalServer(
        private val codes: List<Int>,
    ) {
        val authorizationHeaders = mutableListOf<String?>()

        fun respond(request: Request): Response {
            val code = codes[authorizationHeaders.size]
            authorizationHeaders += request.header("Authorization")
            return Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .body("fixture".toResponseBody())
                .build()
        }
    }
}
