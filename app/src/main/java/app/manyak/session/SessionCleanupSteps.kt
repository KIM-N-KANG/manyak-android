package app.manyak.session

import app.manyak.core.analytics.AnalyticsIdentity
import app.manyak.core.data.api.AuthApi
import app.manyak.core.data.api.dto.LogoutRequestDto
import app.manyak.core.data.datastore.DeviceIdStore
import app.manyak.core.data.provider.ProviderCleanupResult
import app.manyak.core.data.provider.SocialIdTokenProvider
import app.manyak.core.data.session.TokenReadResult
import app.manyak.core.data.session.TokenStorage
import app.manyak.core.data.session.UserScopedStore
import app.manyak.core.domain.auth.AuthProvider
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 종료 정리의 개별 단계. 순서와 저널은 [SessionTerminationCoordinator] 가 소유하고,
 * 여기서는 "이 항목이 실제로 지워졌는가"만 판정한다.
 *
 * 모든 단계는 **여러 번 실행해도 안전하고**, 성공을 확인하지 못하면 false 를 돌려준다.
 * 실패를 삼키고 다음 단계로 넘어가면 이전 사용자의 데이터가 남은 채 로그인 화면이 열린다.
 */
@Singleton
class SessionCleanupSteps
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val tokenStorage: TokenStorage,
        private val userScopedStores: Set<@JvmSuppressWildcards UserScopedStore>,
        private val providers: Map<AuthProvider, @JvmSuppressWildcards SocialIdTokenProvider>,
        private val deviceIdStore: DeviceIdStore,
        private val analyticsIdentity: AnalyticsIdentity,
    ) {
        /** 정리 대상 제공자. 종료를 시작할 때 저널에 대기 목록으로 고정한다. */
        val providerWireNames: List<String> get() = providers.keys.map(AuthProvider::wireName)

        suspend fun storedRefreshToken(): String? =
            (tokenStorage.read() as? TokenReadResult.Available)?.session?.refreshToken

        /** 서버 실패는 로컬 정리를 막지 않는다. 오프라인에서도 로그아웃은 되어야 한다. */
        suspend fun attemptServerLogout(refreshToken: String?) {
            if (refreshToken.isNullOrBlank()) return
            try {
                authApi.logout(LogoutRequestDto(refreshToken))
            } catch (_: IOException) {
                // 비민감 진단만 남기고 사용자에게 알리지 않는다.
            }
        }

        suspend fun clearTokens(): Boolean = retryingCleanup { tokenStorage.clear() }

        /** 한 저장소의 실패가 나머지를 막지 않는다. 모두 시도한 뒤 하나라도 실패하면 이 단계가 실패다. */
        suspend fun clearUserScopedStores(): Boolean =
            userScopedStores
                .map { store -> retryingCleanup { runCatching { store.clearUserData() }.getOrDefault(false) } }
                .all { it }

        /**
         * 두 제공자를 모두 정리하고, 확인하지 못한 제공자를 돌려준다.
         *
         * 확인하지 못했으면 **같은 프로세스에서 먼저 재시도한다** — 앱 재시작까지 미루면 다음 로그인이
         * 이전 사용자의 제공자 세션 위에서 시작될 수 있다.
         */
        suspend fun clearProviders(pending: List<String>): List<String> =
            pending.filter { wireName ->
                val adapter = AuthProvider.fromWireName(wireName)?.let(providers::get) ?: return@filter false
                !retryingCleanup {
                    runCatching { adapter.clearLocalState() }.getOrNull() == ProviderCleanupResult.CLEARED
                }
            }

        /** 저장소와 분석 SDK 가 같은 값을 써야 한다. 저장이 확인된 뒤에만 SDK 에 넘긴다. */
        suspend fun rotateDeviceId(newDeviceId: String): Boolean =
            retryingCleanup { deviceIdStore.replaceWith(newDeviceId) }
                .also { rotated -> if (rotated) analyticsIdentity.setDeviceId(newDeviceId) }
    }

/**
 * 일시적 실패를 유한한 backoff 로 재시도한다.
 *
 * 소진하면 false 이며 호출부가 저널과 세션 장벽을 유지한다 — 무한 재시도로 정리 중 화면에 갇히게
 * 두지 않고, 사용자가 다시 시도할 수 있는 상태로 넘긴다.
 */
internal suspend fun retryingCleanup(action: suspend () -> Boolean): Boolean {
    repeat(CLEANUP_ATTEMPTS) { attempt ->
        if (action()) return true
        if (attempt < CLEANUP_ATTEMPTS - 1) delay(CLEANUP_BACKOFF_MILLIS shl attempt)
    }
    return false
}

private const val CLEANUP_ATTEMPTS = 3
private const val CLEANUP_BACKOFF_MILLIS = 200L
