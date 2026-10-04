package app.manyak.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.manyak.analytics.domain.AnalyticsIdentity
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.data.datastore.DeviceIdStore
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.session.MemberConsent
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsSessionBinderTest {
    @Test
    fun `프로필만으로 식별하지 않고 동의 후 한 번 식별하며 종료와 다음 계정에서 초기화한다`() =
        runTest {
            val identity = RecordingIdentity()
            val consent = FakeConsent()
            val session = FakeSession()
            val profile = FakeProfile()
            val binder =
                AnalyticsSessionBinder(
                    identity,
                    DeviceIdStore(MemoryPreferences(), StandardTestDispatcher(testScheduler)),
                    profile,
                    session,
                    consent,
                    backgroundScope,
                )
            binder.start()
            testScheduler.runCurrent()
            profile.profile.value = user("first")
            session.sessionState.value = SessionState.Member
            testScheduler.runCurrent()
            assertEquals(emptyList<String>(), identity.identified)
            consent.isSatisfied.value = true
            testScheduler.runCurrent()
            assertEquals(listOf("first"), identity.identified)
            profile.profile.value = user("first").copy(nickname = "changed")
            testScheduler.runCurrent()
            assertEquals(listOf("first"), identity.identified)
            session.sessionState.value = SessionState.Undetermined
            testScheduler.runCurrent()
            assertEquals(null, identity.current)
            consent.isSatisfied.value = false
            profile.profile.value = user("second")
            session.sessionState.value = SessionState.Member
            testScheduler.runCurrent()
            assertEquals(null, identity.current)
            consent.isSatisfied.value = true
            testScheduler.runCurrent()
            assertEquals(listOf("first", "second"), identity.identified)
        }
}

private fun user(id: String) = UserProfile(id, "name", null, null, AccountStatus.ACTIVE, 0L, false, emptyList())

private class FakeConsent : MemberConsent {
    override val isSatisfied = MutableStateFlow(false)
}

private class FakeSession : SessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Undetermined)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> =
        DomainResult.Failure(DomainError.Unknown)

    override suspend fun signOut() = Unit

    override suspend fun withdraw(): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun acknowledgeSessionEndNotice() = Unit
}

private class FakeProfile : UserProfileRepository {
    override val profile = MutableStateFlow<UserProfile?>(null)

    override suspend fun refresh(): DomainResult<UserProfile> = DomainResult.Failure(DomainError.Unknown)
}

private class RecordingIdentity : AnalyticsIdentity {
    val identified = mutableListOf<String>()
    var current: String? = null

    override fun setDeviceId(deviceId: String) = Unit

    override fun setUser(userId: String) {
        current = userId
        identified += userId
    }

    override fun clearUser() {
        current = null
    }

    override fun currentSessionId(): Long? = null
}

private class MemoryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(data.value).also {
            data.value =
                it
        }
}
