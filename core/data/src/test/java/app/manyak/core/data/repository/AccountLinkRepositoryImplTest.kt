package app.manyak.core.data.repository

import app.manyak.core.data.api.AccountLinkApi
import app.manyak.core.data.api.dto.LinkReauthRequestDto
import app.manyak.core.data.api.dto.LinkReauthResponseDto
import app.manyak.core.data.api.dto.SocialLoginRequestDto
import app.manyak.core.data.provider.ProviderCleanupResult
import app.manyak.core.data.provider.SocialIdTokenProvider
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class AccountLinkRepositoryImplTest {
    /**
     * 경로는 소문자, 재인증 본문은 서버 enum 이름이다. 표기를 섞으면 재인증이 400 으로 막히는데,
     * 화면에는 일반 실패로만 보여 원인을 찾기 어렵다.
     */
    @Test
    fun `재인증은 대문자 provider 로, 연동은 소문자 경로로 부른다`() =
        runTest {
            val api = FakeAccountLinkApi()

            val result = repository(api).link(AuthProvider.GOOGLE, AuthProvider.KAKAO)

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(LinkReauthRequestDto("GOOGLE", "google-token"), api.reauthRequest)
            assertEquals("kakao", api.linkedProvider)
            assertEquals(SocialLoginRequestDto("kakao-token"), api.linkRequest)
        }

    /** 링크 코드는 두 호출 사이에만 존재한다. 도메인 결과나 상태로는 새어 나가지 않는다. */
    @Test
    fun `재인증이 발급한 링크 코드를 헤더로 넘긴다`() =
        runTest {
            val api = FakeAccountLinkApi()

            repository(api).link(AuthProvider.GOOGLE, AuthProvider.KAKAO)

            assertEquals("link-code-1", api.linkCodeHeader)
        }

    /**
     * 공용 매핑은 403 을 정지 계정으로 접는다. 연동 경로에서 그러면 재인증 실패가 정지 안내로 둔갑하고
     * 409 두 종류도 구분할 수 없다.
     */
    @Test
    fun `403 은 정지 계정이 아니라 사유 코드를 실은 서버 오류로 올린다`() =
        runTest {
            val api = FakeAccountLinkApi(reauthResponse = errorResponse(403, "REAUTH_FAILED"))

            val result = repository(api).link(AuthProvider.GOOGLE, AuthProvider.KAKAO)

            assertEquals(
                DomainResult.Failure(DomainError.Server(status = 403, code = "REAUTH_FAILED", requestId = null)),
                result,
            )
        }

    @Test
    fun `재인증에 실패하면 대상 제공자 창을 열지 않는다`() =
        runTest {
            val api = FakeAccountLinkApi(reauthResponse = errorResponse(403, "REAUTH_FAILED"))
            val kakao = FakeIdTokenProvider(AuthProvider.KAKAO, "kakao-token")

            repository(api, kakao = kakao).link(AuthProvider.GOOGLE, AuthProvider.KAKAO)

            assertEquals(0, kakao.requestCount)
            assertNull(api.linkRequest)
        }

    /** 사용자가 재인증 창을 닫았다. 서버를 부르지 않고 그대로 올려 실패 안내를 띄우지 않게 한다. */
    @Test
    fun `제공자 취소는 서버 호출 없이 그대로 올린다`() =
        runTest {
            val api = FakeAccountLinkApi()
            val google = FakeIdTokenProvider(AuthProvider.GOOGLE, token = null)

            val result = repository(api, google = google).link(AuthProvider.GOOGLE, AuthProvider.KAKAO)

            assertEquals(DomainResult.Failure(DomainError.ProviderCancelled), result)
            assertNull(api.reauthRequest)
        }

    /**
     * 캐시된 토큰은 `iat` 가 오래돼 재인증이 403 으로 막힌다(실측). 연동은 반드시 재발급 경로를 쓴다.
     */
    @Test
    fun `연동은 캐시 가능한 경로가 아니라 재발급 경로로 토큰을 받는다`() =
        runTest {
            val google = FakeIdTokenProvider(AuthProvider.GOOGLE, "google-token")
            val kakao = FakeIdTokenProvider(AuthProvider.KAKAO, "kakao-token")

            repository(FakeAccountLinkApi(), google = google, kakao = kakao)
                .link(AuthProvider.GOOGLE, AuthProvider.KAKAO)

            assertEquals(0, google.cachedRequestCount)
            assertEquals(0, kakao.cachedRequestCount)
            assertEquals(1, google.freshRequestCount)
            assertEquals(1, kakao.freshRequestCount)
        }

    private fun repository(
        api: AccountLinkApi,
        google: SocialIdTokenProvider = FakeIdTokenProvider(AuthProvider.GOOGLE, "google-token"),
        kakao: SocialIdTokenProvider = FakeIdTokenProvider(AuthProvider.KAKAO, "kakao-token"),
    ) = AccountLinkRepositoryImpl(
        accountLinkApi = api,
        providers = mapOf(AuthProvider.GOOGLE to google, AuthProvider.KAKAO to kakao),
    )
}

private fun errorResponse(
    status: Int,
    code: String,
): Response<Nothing> =
    Response.error(
        status,
        """{"status":$status,"code":"$code"}""".toResponseBody("application/json".toMediaType()),
    )

private class FakeAccountLinkApi(
    private val reauthResponse: Response<*>? = null,
) : AccountLinkApi {
    var reauthRequest: LinkReauthRequestDto? = null
    var linkedProvider: String? = null
    var linkCodeHeader: String? = null
    var linkRequest: SocialLoginRequestDto? = null

    @Suppress("UNCHECKED_CAST")
    override suspend fun reauth(request: LinkReauthRequestDto): Response<LinkReauthResponseDto> {
        reauthRequest = request
        return reauthResponse as? Response<LinkReauthResponseDto>
            ?: Response.success(LinkReauthResponseDto(linkCode = "link-code-1"))
    }

    override suspend fun link(
        provider: String,
        linkCode: String,
        request: SocialLoginRequestDto,
    ): Response<Unit> {
        linkedProvider = provider
        linkCodeHeader = linkCode
        linkRequest = request
        return Response.success(201, Unit)
    }
}

/** [token] 이 없으면 사용자가 창을 닫은 것으로 본다. */
private class FakeIdTokenProvider(
    override val provider: AuthProvider,
    private val token: String?,
) : SocialIdTokenProvider {
    var cachedRequestCount: Int = 0
        private set

    var freshRequestCount: Int = 0
        private set

    val requestCount: Int get() = cachedRequestCount + freshRequestCount

    override suspend fun requestIdToken(): DomainResult<String> {
        cachedRequestCount++
        return result()
    }

    override suspend fun requestFreshIdToken(): DomainResult<String> {
        freshRequestCount++
        return result()
    }

    override suspend fun clearLocalState(): ProviderCleanupResult = ProviderCleanupResult.CLEARED

    private fun result(): DomainResult<String> =
        token?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.ProviderCancelled)
}
