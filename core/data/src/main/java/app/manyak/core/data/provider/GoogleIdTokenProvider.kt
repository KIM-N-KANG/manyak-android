package app.manyak.core.data.provider

import android.content.Context
import android.util.Base64
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
import java.security.SecureRandom
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

        override suspend fun requestIdToken(): DomainResult<String> = requestIdToken(nonce = null)

        /**
         * Credential Manager 는 유효기간이 남은 ID 토큰을 캐시에서 그대로 돌려준다(실측 — 31분 전
         * 발급 토큰 재사용). nonce 를 요구하면 그 값이 토큰 클레임에 실려야 하므로 캐시로는 만족할 수
         * 없고 새 `iat` 로 다시 발급된다. 서버는 이 클레임을 검사하지 않고 무시한다.
         */
        override suspend fun requestFreshIdToken(): DomainResult<String> = requestIdToken(nonce = newNonce())

        private suspend fun requestIdToken(nonce: String?): DomainResult<String> {
            if (config.googleServerClientId.isBlank()) {
                return DomainResult.Failure(DomainError.ProviderNotConfigured(provider))
            }
            val activity =
                activityProvider.currentActivity()
                    ?: return DomainResult.Failure(DomainError.ProviderFailed(provider, "no-activity"))

            val option =
                GetSignInWithGoogleOption
                    .Builder(config.googleServerClientId)
                    .apply { nonce?.let(::setNonce) }
                    .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

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

        private fun newNonce(): String {
            val bytes = ByteArray(NONCE_BYTES)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private companion object {
            /** 캐시 우회에만 쓰는 값이라 서버가 검사하지 않는다. 추측 불가능하기만 하면 된다. */
            const val NONCE_BYTES = 16

            val GOOGLE_ID_TOKEN_TYPES =
                setOf(
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
                )
        }
    }
