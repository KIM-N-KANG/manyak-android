package app.manyak.network.data.interceptor

import app.manyak.network.data.api.HEADER_SESSION_ID
import app.manyak.network.domain.SessionIdAccess
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class SessionIdInterceptor
    @Inject
    constructor(
        private val sessions: SessionIdAccess,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder().removeHeader(HEADER_SESSION_ID)
            sessions.currentSessionId()?.takeIf { it >= 0 }?.let { request.header(HEADER_SESSION_ID, it.toString()) }
            return chain.proceed(request.build())
        }
    }
