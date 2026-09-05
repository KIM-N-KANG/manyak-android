package app.manyak.core.data.repository

import app.manyak.common.domain.auth.AccountLinkRepository
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.core.data.api.AccountLinkApi
import app.manyak.core.data.api.HEADER_REQUEST_ID
import app.manyak.core.data.api.HTTP_UNAUTHORIZED
import app.manyak.core.data.api.apiCall
import app.manyak.core.data.api.dto.LinkReauthRequestDto
import app.manyak.core.data.api.dto.SocialLoginRequestDto
import app.manyak.core.data.api.emptyBodyApiCall
import app.manyak.core.data.api.parseErrorCode
import app.manyak.core.data.provider.SocialIdTokenProvider
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 재인증 → 연동의 2단계 프로토콜.
 *
 * 제공자 SDK 인증은 [SocialIdTokenProvider] 만 쓰고 **서버 로그인은 호출하지 않는다** — 로그인 경로를
 * 타면 세션이 연동 대상 계정으로 교체된다.
 *
 * 링크 코드는 두 호출 사이에만 존재하는 단명 값이라 [link] 의 지역 변수로만 두고, 상태나 도메인
 * 결과로 올리지 않는다.
 */
@Singleton
class AccountLinkRepositoryImpl
    @Inject
    constructor(
        private val accountLinkApi: AccountLinkApi,
        private val providers: Map<AuthProvider, @JvmSuppressWildcards SocialIdTokenProvider>,
    ) : AccountLinkRepository {
        override suspend fun link(
            current: AuthProvider,
            target: AuthProvider,
        ): DomainResult<Unit> {
            val reauthToken =
                when (val authenticated = requestIdToken(current)) {
                    is DomainResult.Success -> authenticated.value
                    is DomainResult.Failure -> return authenticated
                }

            val reauth =
                apiCall({ toAccountLinkError() }) {
                    accountLinkApi.reauth(LinkReauthRequestDto(current.reauthBodyName, reauthToken))
                }
            val linkCode =
                when (reauth) {
                    is DomainResult.Success -> reauth.value.linkCode
                    is DomainResult.Failure -> return reauth
                }

            val targetToken =
                when (val authenticated = requestIdToken(target)) {
                    is DomainResult.Success -> authenticated.value
                    is DomainResult.Failure -> return authenticated
                }

            return emptyBodyApiCall({ toAccountLinkError() }) {
                accountLinkApi.link(target.wireName, linkCode, SocialLoginRequestDto(targetToken))
            }
        }

        /**
         * 두 단계 모두 방금 발급된 토큰을 쓴다. 재인증은 서버가 `iat` 신선도를 요구하고, 대상 토큰도
         * 캐시에서 오면 만료된 토큰을 제출하게 된다.
         */
        private suspend fun requestIdToken(provider: AuthProvider): DomainResult<String> =
            providers[provider]
                ?.requestFreshIdToken()
                ?: DomainResult.Failure(DomainError.ProviderFailed(provider, "no-adapter"))
    }

/**
 * 재인증 본문의 provider 는 경로 파라미터([AuthProvider.wireName], 소문자)와 달리 서버 enum 이름을
 * 그대로 받는다. 한 흐름 안에서 두 표기가 갈리므로 바꾸는 자리를 하나로 둔다.
 */
private val AuthProvider.reauthBodyName: String get() = name

/**
 * 연동 경로의 403 은 정지 계정이 아니라 재인증·소셜 토큰 실패다. 공용 매핑처럼 접어 버리면 사유를
 * 구분할 수도, 정지 안내와 섞이는 것을 막을 수도 없다.
 *
 * 401 만 세션 문제로 남긴다 — 서버는 계정 자체가 없는 경우(부재·삭제)에만 이 경로에서 401 을 쓴다.
 */
private fun Response<*>.toAccountLinkError(): DomainError {
    val code = parseErrorCode()
    return if (code() == HTTP_UNAUTHORIZED) {
        DomainError.Unauthorized
    } else {
        DomainError.Server(status = code(), code = code, requestId = headers()[HEADER_REQUEST_ID])
    }
}
