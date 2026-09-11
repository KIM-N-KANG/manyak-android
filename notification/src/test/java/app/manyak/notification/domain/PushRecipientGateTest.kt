package app.manyak.notification.domain

import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.notification.entity.PushMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 수신자 판정 — 현재 회원의 프로필 ID 와 같을 때만 통과한다. */
class PushRecipientGateTest {
    @Test
    fun `수신자 ID가 없으면 통과하지 않는다`() =
        runTest {
            val gate = gate(SessionState.Member, profile("me"))
            assertFalse(gate.admits(null))
        }

    @Test
    fun `수신자가 현재 회원과 다르면 통과하지 않는다`() =
        runTest {
            val gate = gate(SessionState.Member, profile("me"))
            assertFalse(gate.admits("other"))
            assertTrue(gate.admits("me"))
        }

    @Test
    fun `세션이 미확정이면 확정될 때까지 기다리고 미로그인이면 버린다`() =
        runTest {
            val session = FakeSessionRepository(SessionState.Undetermined)
            val gate = PushRecipientGate(session, FakeProfileRepository(null))
            val pending = async { gate.admits("me") }
            testScheduler.runCurrent()
            assertFalse(pending.isCompleted)

            session.state.value = SessionState.SignedOut(null)
            testScheduler.runCurrent()
            assertFalse(pending.await())
        }

    @Test
    fun `프로필을 기다리는 사이 로그아웃이 시작되면 통과하지 않는다`() =
        runTest {
            val session = FakeSessionRepository(SessionState.Member)
            val profiles = FakeProfileRepository(null)
            val gate = PushRecipientGate(session, profiles)
            val pending = async { gate.admits("me") }
            testScheduler.runCurrent()

            session.state.value = SessionState.Undetermined
            profiles.current.value = profile("me")
            testScheduler.runCurrent()
            assertFalse(pending.await())
        }

    @Test
    fun `type이 없는 페이로드는 메시지가 아니고 대상 식별자는 type별 키에서 읽는다`() {
        assertNull(PushMessage.from(mapOf("title" to "x")))
        val story = PushMessage.from(mapOf("type" to "STORY_COMPLETED", "storyId" to "s1", "recipientId" to "u"))
        assertEquals("s1", story?.targetId)
        assertEquals("u", story?.recipientId)
        val reminder = PushMessage.from(mapOf("type" to "ATTENDANCE_REMINDER", "date" to "2026-09-08"))
        assertEquals("2026-09-08", reminder?.targetId)
        assertNull(PushMessage.from(mapOf("type" to "NEW_TYPE", "storyId" to "s1"))?.targetId)
    }

    private fun gate(
        state: SessionState,
        profile: UserProfile?,
    ) = PushRecipientGate(FakeSessionRepository(state), FakeProfileRepository(profile))

    private fun profile(id: String) =
        UserProfile(
            id = id,
            nickname = "n",
            profileImageUrl = null,
            profileThumbnailBase64 = null,
            status = AccountStatus.ACTIVE,
            creditBalance = 0,
            attendedToday = false,
            linkedProviders = emptyList(),
        )
}

private class FakeSessionRepository(
    initial: SessionState,
) : SessionRepository {
    val state = MutableStateFlow(initial)
    override val sessionState: StateFlow<SessionState> get() = state
    override val signInInProgress: StateFlow<AuthProvider?> = MutableStateFlow(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun signOut() = Unit

    override suspend fun withdraw(): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun acknowledgeSessionEndNotice() = Unit
}

private class FakeProfileRepository(
    initial: UserProfile?,
) : UserProfileRepository {
    val current = MutableStateFlow(initial)
    override val profile: StateFlow<UserProfile?> get() = current

    override suspend fun refresh(): DomainResult<UserProfile> = DomainResult.Failure(DomainError.Unknown)
}
