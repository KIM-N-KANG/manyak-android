package app.manyak.my.profile.data.repository

import app.manyak.auth.domain.SessionEndSignal
import app.manyak.auth.domain.SessionGate
import app.manyak.common.data.di.ApplicationScope
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.session.SessionEndNotice
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.my.profile.data.api.ProfileApi
import app.manyak.my.profile.data.datastore.ProfileCacheStore
import app.manyak.my.profile.data.dto.MeResponseDto
import app.manyak.network.data.api.apiCall
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 프로필의 단일 보관소. 화면마다 `/auth/me` 를 부르지 않는다.
 *
 * **조회 실패는 세션 상태를 바꾸지 않는다** — 비행기 모드에서 앱을 열면 로그아웃된 것처럼
 * 보이면 안 된다. 그래서 이 클래스는 세션을 끝내지 않고 판정만 결과 타입으로 올린다.
 *
 * 정지 계정은 예외다. `status=SUSPENDED` 는 **갱신 성공이 아니라** 세션이 끝나야 한다는 신호이므로,
 * 캐시에 남기지 않고 [DomainError.AccountSuspended] 로 돌려준다. 어느 호출부에서 확인되든 같은
 * 종료 절차를 타도록 종료 신호도 함께 보낸다 — 화면 계층 조정자를 직접 알지는 않는다.
 */
@Singleton
class UserProfileRepositoryImpl
    @Inject
    constructor(
        private val userApi: ProfileApi,
        private val cache: ProfileCacheStore,
        private val gate: SessionGate,
        private val sessionEndSignal: Lazy<SessionEndSignal>,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : UserProfileRepository {
        override val profile: StateFlow<UserProfile?> =
            cache.cached.stateIn(applicationScope, SharingStarted.Eagerly, null)

        override suspend fun refresh(): DomainResult<UserProfile> {
            val generation = gate.currentGeneration
            val result = apiCall { userApi.me() }.map(MeResponseDto::toDomain)
            if (result !is DomainResult.Success) return result

            if (result.value.status == AccountStatus.SUSPENDED) {
                // 정지 계정의 프로필은 캐시하지 않는다. 종료 정리가 지울 데이터를 새로 만들 이유가 없다.
                sessionEndSignal.get().onSessionInvalidated(SessionEndNotice.ACCOUNT_SUSPENDED, null)
                return DomainResult.Failure(DomainError.AccountSuspended)
            }
            // 응답이 늦게 도착해 정리 뒤에 캐시를 되살리지 않도록 커밋 직전에 세대를 다시 본다.
            if (gate.isCurrentGeneration(generation)) cache.save(result.value)
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
