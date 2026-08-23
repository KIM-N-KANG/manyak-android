package app.manyak.core.data.provider

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import app.manyak.core.data.di.SocialAuthConfig
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credential Manager + Sign in with Google.
 *
 * 구 Google Sign-In SDK 는 지원이 중단되어 쓰지 않는다. 요청에 싣는 것은
 * **서버(웹) 클라이언트 ID** 이며 이 값이 ID 토큰의 `aud` 가 된다 — Android 클라이언트 ID 는 `azp` 로
 * 따로 실려 서버가 검사한다.
 *
 * 정리 실패는 확인 불가로 본다. Google 은 캐시가 지워졌는지 알 수 없으므로 정리 대기로 남긴다.
 */
@Singleton
class GoogleIdTokenProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val activityProvider: ActivityProvider,
        private val config: SocialAuthConfig,
    ) : SocialIdTokenProvider {
        override val provider: AuthProvider = AuthProvider.GOOGLE

        private val credentialManager by lazy { CredentialManager.create(context) }

        override suspend fun requestIdToken(): DomainResult<String> {
            if (config.googleServerClientId.isBlank()) {
                return DomainResult.Failure(DomainError.ProviderNotConfigured(provider))
            }
            val activity =
                activityProvider.currentActivity()
                    ?: return DomainResult.Failure(DomainError.ProviderFailed(provider, "no-activity"))

            val request =
                GetCredentialRequest
                    .Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(config.googleServerClientId).build())
                    .build()

            return try {
                extractIdToken(credentialManager.getCredential(activity, request).credential)
            } catch (_: GetCredentialCancellationException) {
                // 사용자가 스스로 닫았다. 실패 안내를 띄우지 않는다.
                DomainResult.Failure(DomainError.ProviderCancelled)
            } catch (cause: GetCredentialException) {
                DomainResult.Failure(DomainError.ProviderFailed(provider, cause::class.simpleName))
            }
        }

        override suspend fun clearLocalState(): ProviderCleanupResult =
            try {
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE),
                )
                ProviderCleanupResult.CLEARED
            } catch (_: ClearCredentialException) {
                ProviderCleanupResult.RETRY_REQUIRED
            }

        private fun extractIdToken(credential: Credential): DomainResult<String> {
            if (credential !is CustomCredential || credential.type !in GOOGLE_ID_TOKEN_TYPES) {
                return DomainResult.Failure(DomainError.ProviderFailed(provider, "unexpected-credential"))
            }
            return DomainResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        }

        private companion object {
            val GOOGLE_ID_TOKEN_TYPES =
                setOf(
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
                )
        }
    }
