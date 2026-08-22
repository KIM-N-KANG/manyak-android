package app.manyak.core.data.repository

import app.manyak.core.data.api.UserApi
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.MeResponseDto
import app.manyak.core.data.datastore.ProfileCacheStore
import app.manyak.core.data.di.ApplicationScope
import app.manyak.core.data.session.SessionStateHolder
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.map
import app.manyak.core.domain.error.valueOrNull
import app.manyak.core.domain.user.AccountStatus
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.domain.user.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 프로필의 단일 보관소. 화면마다 `/auth/me` 를 부르지 않는다.
 *
 * **조회 실패는 세션 상태를 바꾸지 않는다**(하네스 §3-3-4) — 비행기 모드에서 앱을 열면 로그아웃된 것처럼
 * 보이면 안 된다. 그래서 이 클래스는 세션 상태를 쓰지 않고 읽기만 한다(세대 확인용).
 */
@Singleton
class UserProfileRepositoryImpl
    @Inject
    constructor(
        private val userApi: UserApi,
        private val cache: ProfileCacheStore,
        private val sessionStateHolder: SessionStateHolder,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : UserProfileRepository {
        override val profile: StateFlow<UserProfile?> =
            cache.cached.stateIn(applicationScope, SharingStarted.Eagerly, null)

        override suspend fun refresh(): DomainResult<UserProfile> {
            val generation = sessionStateHolder.currentGeneration
            val result = apiCall { userApi.me() }.map(MeResponseDto::toDomain)
            val profile = result.valueOrNull()
            // 응답이 늦게 도착해 정리 뒤에 캐시를 되살리지 않도록 커밋 직전에 세대를 다시 본다.
            if (profile != null && sessionStateHolder.isCurrentGeneration(generation)) {
                cache.save(profile)
            }
            return result
        }
    }

private fun MeResponseDto.toDomain(): UserProfile =
    UserProfile(
        id = id,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        profileThumbnailBase64 = profileThumbnailBase64,
        // 서버가 앱이 모르는 상태를 보내도 로그인을 막지 않는다.
        status = runCatching { AccountStatus.valueOf(status) }.getOrDefault(AccountStatus.UNKNOWN),
        creditBalance = creditBalance,
        attendedToday = attendedToday,
        linkedProviders = linkedProviders.mapNotNull(AuthProvider::fromWireName),
    )
