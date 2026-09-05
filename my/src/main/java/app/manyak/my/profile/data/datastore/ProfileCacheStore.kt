package app.manyak.my.profile.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.my.data.di.ProfileDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 마지막으로 성공한 프로필 응답을 보관해 조회 실패·오프라인에서 표시한다.
 *
 * **사용자 귀속 데이터이므로 세션 종료 정리 대상이다**. 남으면 공용 기기의 다음
 * 사용자에게 이전 회원의 닉네임·프로필이 보인다.
 */
@Singleton
class ProfileCacheStore
    @Inject
    constructor(
        @param:ProfileDataStore private val dataStore: DataStore<Preferences>,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : UserScopedStore {
        override val storeName: String = "profile-cache"

        val cached: Flow<UserProfile?> =
            dataStore.data.map { preferences ->
                preferences[PROFILE_KEY]?.let { raw ->
                    runCatching { json.decodeFromString<CachedProfile>(raw) }.getOrNull()?.toDomain()
                }
            }

        suspend fun save(profile: UserProfile) {
            withContext(ioDispatcher) {
                runCatching {
                    dataStore.edit { it[PROFILE_KEY] = json.encodeToString(CachedProfile.from(profile)) }
                }
            }
        }

        /** 실패를 삼키지 않는다. 종료 흐름이 재시도하고, 성공 전에는 다음 단계로 넘어가지 않는다. */
        override suspend fun clearUserData(): Boolean =
            withContext(ioDispatcher) {
                runCatching { dataStore.edit { it.remove(PROFILE_KEY) } }.isSuccess
            }

        @Serializable
        private data class CachedProfile(
            val id: String,
            val nickname: String,
            val profileImageUrl: String? = null,
            val profileThumbnailBase64: String? = null,
            val status: String,
            val creditBalance: Long = 0,
            val attendedToday: Boolean = false,
            val linkedProviders: List<String> = emptyList(),
        ) {
            fun toDomain(): UserProfile =
                UserProfile(
                    id = id,
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                    profileThumbnailBase64 = profileThumbnailBase64,
                    status = runCatching { AccountStatus.valueOf(status) }.getOrDefault(AccountStatus.UNKNOWN),
                    creditBalance = creditBalance,
                    attendedToday = attendedToday,
                    linkedProviders = linkedProviders.mapNotNull(AuthProvider::fromWireName),
                )

            companion object {
                fun from(profile: UserProfile): CachedProfile =
                    CachedProfile(
                        id = profile.id,
                        nickname = profile.nickname,
                        profileImageUrl = profile.profileImageUrl,
                        profileThumbnailBase64 = profile.profileThumbnailBase64,
                        status = profile.status.name,
                        creditBalance = profile.creditBalance,
                        attendedToday = profile.attendedToday,
                        linkedProviders = profile.linkedProviders.map { it.wireName },
                    )
            }
        }

        private companion object {
            val PROFILE_KEY = stringPreferencesKey("profile")
            val json = Json { ignoreUnknownKeys = true }
        }
    }
