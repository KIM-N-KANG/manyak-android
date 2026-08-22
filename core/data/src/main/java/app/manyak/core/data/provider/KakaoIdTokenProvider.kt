package app.manyak.core.data.provider

import android.content.Context
import app.manyak.core.data.di.SocialAuthConfig
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.error.errorOrNull
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Kakao Android SDK v2(OIDC).
 *
 * 카카오톡 앱 간 로그인을 먼저 시도하고, **실패했을 때만** 카카오계정 로그인으로 넘긴다. 다만
 * **사용자가 명시적으로 취소하면 폴백하지 않는다** — 취소를 폴백으로 처리하면
 * 사용자가 로그인을 그만둘 방법이 없어진다.
 *
 * SDK `logout()` 은 요청 성공 여부와 무관하게 SDK 저장 토큰을 폐기한다. 그래서 콜백이 오류를 주어도
 * 로컬 정리는 완료로 체크포인트하고 원격 실패만 무시한다.
 */
@Singleton
class KakaoIdTokenProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val activityProvider: ActivityProvider,
        private val config: SocialAuthConfig,
    ) : SocialIdTokenProvider {
        override val provider: AuthProvider = AuthProvider.KAKAO

        @Volatile
        private var initialized: Boolean = false

        override suspend fun requestIdToken(): DomainResult<String> {
            if (config.kakaoNativeAppKey.isBlank()) {
                return DomainResult.Failure(DomainError.ProviderNotConfigured(provider))
            }
            ensureInitialized()
            val activity =
                activityProvider.currentActivity()
                    ?: return DomainResult.Failure(DomainError.ProviderFailed(provider, "no-activity"))

            return withContext(Dispatchers.Main) {
                if (!UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
                    return@withContext loginWithKakaoAccount(activity)
                }
                val talkResult = loginWithKakaoTalk(activity)
                // 취소가 아니면 카카오톡 미설치·미로그인으로 보고 카카오계정 로그인으로 넘긴다.
                if (talkResult.errorOrNull() == DomainError.ProviderCancelled) {
                    talkResult
                } else {
                    talkResult.recoverWithAccountLogin(activity)
                }
            }
        }

        override suspend fun clearLocalState(): ProviderCleanupResult {
            if (config.kakaoNativeAppKey.isBlank()) return ProviderCleanupResult.CLEARED
            ensureInitialized()
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    UserApiClient.instance.logout { _ ->
                        // 원격 폐기 실패와 무관하게 SDK 저장 토큰은 지워진다.
                        continuation.resume(ProviderCleanupResult.CLEARED)
                    }
                }
            }
        }

        private suspend fun DomainResult<String>.recoverWithAccountLogin(activity: android.app.Activity) =
            if (this is DomainResult.Success) this else loginWithKakaoAccount(activity)

        private suspend fun loginWithKakaoTalk(activity: android.app.Activity): DomainResult<String> =
            suspendCancellableCoroutine { continuation ->
                UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                    continuation.resume(toResult(token, error))
                }
            }

        private suspend fun loginWithKakaoAccount(activity: android.app.Activity): DomainResult<String> =
            suspendCancellableCoroutine { continuation ->
                UserApiClient.instance.loginWithKakaoAccount(activity) { token, error ->
                    continuation.resume(toResult(token, error))
                }
            }

        private fun toResult(
            token: OAuthToken?,
            error: Throwable?,
        ): DomainResult<String> {
            val cancelled = error is ClientError && error.reason == ClientErrorCause.Cancelled
            val idToken = token?.idToken
            return when {
                cancelled -> DomainResult.Failure(DomainError.ProviderCancelled)
                idToken != null -> DomainResult.Success(idToken)
                // OIDC 가 꺼져 있으면 토큰은 오지만 idToken 이 비어 서버 로그인이 불가능하다.
                error == null -> DomainResult.Failure(DomainError.ProviderFailed(provider, "missing-id-token"))
                else -> DomainResult.Failure(DomainError.ProviderFailed(provider, error::class.simpleName))
            }
        }

        private fun ensureInitialized() {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                KakaoSdk.init(context, config.kakaoNativeAppKey)
                initialized = true
            }
        }
    }
