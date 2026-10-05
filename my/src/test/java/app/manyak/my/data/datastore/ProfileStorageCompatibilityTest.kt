package app.manyak.my.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.my.invite.data.datastore.InviteOnboardingStore
import app.manyak.my.profile.data.datastore.ProfileCacheStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileStorageCompatibilityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `초대 안내 닫기 쓰기 실패를 반환하고 취소는 전파한다`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val failed = InviteOnboardingStore(FailingOnboardingDataStore(IOException("write failed")), dispatcher)
            assertFalse(failed.acknowledge())
            val cancelled =
                InviteOnboardingStore(FailingOnboardingDataStore(CancellationException("cancelled")), dispatcher)
            try {
                cancelled.acknowledge()
                throw AssertionError("Cancellation must propagate")
            } catch (_: CancellationException) {
                // 코루틴 취소는 사용자에게 표시할 저장 실패가 아니다.
            }
        }

    @Test
    fun `이전 프로필 JSON과 초대 안내 키를 읽고 같은 형식으로 저장한다`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    temporaryFolder.newFile("profile.preferences_pb")
                }
            dataStore.edit {
                it[stringPreferencesKey("profile")] = legacyProfile
                it[booleanPreferencesKey("invite_onboarding_pending")] = true
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cache = ProfileCacheStore(dataStore, dispatcher)
            val onboarding = InviteOnboardingStore(dataStore, dispatcher)

            val profile = requireNotNull(cache.cached.first())
            assertEquals("user-fixture", profile.id)
            assertEquals(listOf(AuthProvider.GOOGLE), profile.linkedProviders)
            assertTrue(onboarding.pending.first())

            cache.save(profile)
            val saved = requireNotNull(dataStore.data.first()[stringPreferencesKey("profile")])
            assertEquals(Json.parseToJsonElement(legacyProfile), Json.parseToJsonElement(saved))
            assertTrue(onboarding.pending.first())
        }

    @Test
    fun `같은 파일의 프로필과 안내 표시는 각 저장소가 자기 키만 정리한다`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    temporaryFolder.newFile("profile.preferences_pb")
                }
            dataStore.edit { it[stringPreferencesKey("profile")] = legacyProfile }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cache = ProfileCacheStore(dataStore, dispatcher)
            val onboarding = InviteOnboardingStore(dataStore, dispatcher)
            onboarding.markPending()

            assertTrue(cache.clearUserData())
            assertNull(cache.cached.first())
            assertTrue(onboarding.pending.first())
            assertTrue(onboarding.clearUserData())
            assertFalse(onboarding.pending.first())
            assertEquals("profile-cache", cache.storeName)
            assertEquals("invite-onboarding", onboarding.storeName)
        }
}

private val legacyProfile =
    """
    {"id":"user-fixture","nickname":"fixture","status":"ACTIVE",
    "creditBalance":12,"attendedToday":true,"linkedProviders":["google"]}
    """.trimIndent()

private class FailingOnboardingDataStore(
    private val failure: Exception,
) : DataStore<Preferences> {
    override val data = flowOf(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
}
