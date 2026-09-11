package app.manyak.notification.data

import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionGate
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.notification.data.api.PushTokenApi
import app.manyak.notification.data.api.dto.PushTokenDeleteRequestDto
import app.manyak.notification.data.api.dto.PushTokenRegisterRequestDto
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

/** 등록 계기 둘과 로그아웃 순서(닫기 → 취소 → 삭제)를 고정한다. */
class PushTokenRegistrarImplTest {
    @Test
    fun `회원으로 바뀌면 현재 토큰을 등록한다`() =
        runTest {
            val session = FakeSessionRepository()
            val api = FakeApi()
            registrar(session, api, this).start()

            session.state.value = SessionState.Member
            testScheduler.runCurrent()

            assertEquals(listOf("tok"), api.registered)
        }

    @Test
    fun `토큰 갱신은 회원일 때만 등록한다`() =
        runTest {
            val session = FakeSessionRepository()
            val api = FakeApi()
            val registrar = registrar(session, api, this).apply { start() }

            registrar.onTokenRefreshed()
            testScheduler.runCurrent()
            assertEquals(0, api.registered.size)

            session.state.value = SessionState.Member
            testScheduler.runCurrent()
            registrar.onTokenRefreshed()
            testScheduler.runCurrent()
            assertEquals(2, api.registered.size)
        }

    @Test
    fun `등록이 403이면 정지 종료 신호를 보낸다`() =
        runTest {
            val session = FakeSessionRepository()
            val api = FakeApi(registerResponse = { Response.error(403, "".toResponseBody(JSON)) })
            val signal = RecordingSignal()
            registrar(session, api, this, signal).start()

            session.state.value = SessionState.Member
            testScheduler.runCurrent()

            assertEquals(listOf(SessionEndNotice.ACCOUNT_SUSPENDED), signal.notices)
        }

    @Test
    fun `닫으면 진행 중인 등록을 취소하고 삭제를 한 번 보내며 이후 갱신은 무시한다`() =
        runTest {
            val session = FakeSessionRepository()
            val inFlight = CompletableDeferred<Unit>()
            val api =
                FakeApi(registerResponse = {
                    inFlight.await()
                    Response.success(204, Unit)
                })
            val registrar = registrar(session, api, this).apply { start() }
            session.state.value = SessionState.Member
            testScheduler.runCurrent()
            assertEquals(1, api.registerStarted)

            registrar.closeAndDeleteToken()
            registrar.onTokenRefreshed()
            testScheduler.runCurrent()

            assertEquals(listOf("tok"), api.deleted)
            assertEquals(0, api.registered.size)
            assertEquals(1, api.registerStarted)
        }

    private fun registrar(
        session: FakeSessionRepository,
        api: FakeApi,
        scope: TestScope,
        signal: SessionEndSignal = RecordingSignal(),
    ) = PushTokenRegistrarImpl(
        sessionRepository = session,
        gate = SessionGate(),
        sessionEndSignal = Lazy { signal },
        api = api,
        tokens = FcmTokenSource { "tok" },
        applicationScope = scope.backgroundScope,
    )
}

private val JSON = "application/json".toMediaType()

private class FakeSessionRepository : SessionRepository {
    val state = MutableStateFlow<SessionState>(SessionState.SignedOut(null))
    override val sessionState: StateFlow<SessionState> get() = state
    override val signInInProgress: StateFlow<AuthProvider?> = MutableStateFlow(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun signOut() = Unit

    override suspend fun withdraw(): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun acknowledgeSessionEndNotice() = Unit
}

private class FakeApi(
    private val registerResponse: suspend () -> Response<Unit> = { Response.success(204, Unit) },
) : PushTokenApi {
    val registered = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var registerStarted = 0

    override suspend fun register(request: PushTokenRegisterRequestDto): Response<Unit> {
        registerStarted++
        val response = registerResponse()
        if (response.isSuccessful) registered += request.token
        return response
    }

    override suspend fun delete(request: PushTokenDeleteRequestDto): Response<Unit> {
        deleted += request.token
        return Response.success(204, Unit)
    }
}

private class RecordingSignal : SessionEndSignal {
    val notices = mutableListOf<SessionEndNotice>()

    override fun onSessionInvalidated(
        notice: SessionEndNotice,
        serverLogoutToken: String?,
    ) {
        notices += notice
    }
}
