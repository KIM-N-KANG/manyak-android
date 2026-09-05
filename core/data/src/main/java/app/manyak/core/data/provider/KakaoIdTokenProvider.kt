package app.manyak.core.data.provider

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.errorOrNull
import app.manyak.common.entity.auth.AuthProvider
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
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
 * SDK `logout()` 은 요청 성공 여부와 무관하게 SDK 저장 토큰을 폐기하지만, 그렇게 판정할 수 있는 것은
 * 콜백을 받았을 때뿐이다. 콜백 자체가 오지 않았거나 SDK 가 초기화되지 않았다면 정리 대기로 남긴다.
 *
 * SDK 초기화는 [KakaoSdkInitializer] 가 앱 시작에서 이미 끝낸다. 여기서 다시 부르는 것은 멱등한
 * 확인일 뿐이며, 초기화되지 않았다면(앱 키 미주입) 조용히 기다리지 않고 즉시 실패로 드러낸다.
 */
@Singleton
class KakaoIdTokenProvider
    @Inject
    constructor(
        private val activityProvider: ActivityProvider,
        private val initializer: KakaoSdkInitializer,
    ) : SocialIdTokenProvider {
        override val provider: AuthProvider = AuthProvider.KAKAO

        override suspend fun requestIdToken(): DomainResult<String> {
            if (!initializer.initialize()) {
                return DomainResult.Failure(DomainError.ProviderNotConfigured(provider))
            }
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

        /**
         * Kakao SDK 의 `login*` 은 저장된 토큰을 돌려주는 API 가 아니라 매번 인가를 다시 거쳐 토큰을
         * 발급한다. 그래서 일반 경로와 같고, 캐시 우회를 위해 추가로 할 일이 없다.
         */
        override suspend fun requestFreshIdToken(): DomainResult<String> = requestIdToken()

        override suspend fun clearLocalState(): ProviderCleanupResult {
            // 초기화되지 않았다면 SDK 가 토큰을 들고 있을 수도 없다. 지울 상태가 없으므로 완료다.
            if (!initializer.initialize()) return ProviderCleanupResult.CLEARED
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    UserApiClient.instance.logout { _ ->
                        // 원격 폐기 실패와 무관하게 SDK 저장 토큰은 지워진다.
                        if (continuation.isActive) continuation.resume(ProviderCleanupResult.CLEARED)
                    }
                }
            }
        }

        private suspend fun DomainResult<String>.recoverWithAccountLogin(activity: android.app.Activity) =
            if (this is DomainResult.Success) this else loginWithKakaoAccount(activity)

        private suspend fun loginWithKakaoTalk(activity: android.app.Activity): DomainResult<String> =
            suspendCancellableCoroutine { continuation ->
                UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                    // 프로세스 재생성 뒤 늦게 도착한 콜백은 기다리는 곳이 없다. 두 번 재개하지 않는다.
                    if (continuation.isActive) continuation.resume(toResult(token, error))
                }
            }

        private suspend fun loginWithKakaoAccount(activity: android.app.Activity): DomainResult<String> =
            suspendCancellableCoroutine { continuation ->
                UserApiClient.instance.loginWithKakaoAccount(activity) { token, error ->
                    if (continuation.isActive) continuation.resume(toResult(token, error))
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
    }
