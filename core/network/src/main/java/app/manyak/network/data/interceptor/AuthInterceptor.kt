package app.manyak.network.data.interceptor

import app.manyak.network.domain.SessionTokenAccess
import app.manyak.network.entity.TokenAccess
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/** 쓸 수 있는 세션 토큰이 없어 보호 요청을 보내지 않았다. */
class SessionUnavailableException(
    val access: TokenAccess,
) : IOException("사용할 수 있는 인증 토큰이 없어 요청을 보내지 않았다")

/**
 * 보호 요청에 access 토큰을 붙인다.
 *
 * 붙이기 전에 만료를 판정해 **필요하면 먼저 재발급한다**. 서버의 선택적 인증 경로가 만료 토큰을
 * 익명으로 통과시키기 때문에, 응답을 보고 재발급하는 방식만으로는 회원 콘텐츠가 주인 없이 저장된다.
 *
 * 선제 재발급을 거쳤는데도 401 이면(서버 측 계열 폐기 등) 재발급 **1회** 후 원 요청을 **1회만** 재시도한다.
 *
 * 재시도는 **요청을 보내기 전에 읽은 세션 세대**에 묶인다. 그 사이 로그아웃과 새 로그인이 끝났다면
 * 이전 사용자의 401 처리가 새 사용자의 토큰으로 재시도되므로, 세대가 다르면 재시도하지 않는다.
 */
class AuthInterceptor
    @Inject
    constructor(
        private val tokenManager: SessionTokenAccess,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val generation = tokenManager.currentGeneration
            val access = runBlocking { tokenManager.accessToken() }
            val accessToken =
                (access as? TokenAccess.Available)?.accessToken ?: throw SessionUnavailableException(access)

            val response = chain.proceed(chain.request().withBearer(accessToken))
            if (response.code != HTTP_UNAUTHORIZED) return response

            val retryAccess = runBlocking { tokenManager.refreshAfterUnauthorized(generation) }
            val retryToken = (retryAccess as? TokenAccess.Available)?.accessToken ?: return response

            response.close()
            return chain.proceed(chain.request().withBearer(retryToken))
        }

        private fun Request.withBearer(accessToken: String): Request =
            newBuilder().header(HEADER_AUTHORIZATION, "Bearer $accessToken").build()

        private companion object {
            const val HTTP_UNAUTHORIZED = 401
            const val HEADER_AUTHORIZATION = "Authorization"
        }
    }
