package app.manyak.create.general.data.api

import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** 서버 수락 여부를 모르는 쓰기는 HTTP 503 응답의 Retry-After가 있어도 자동으로 반복하지 않는다. */
class GeneralSubmissionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().method(request.method, SingleSubmissionBody(body)).build())
    }
}

internal class SingleSubmissionBody(
    private val original: RequestBody,
) : RequestBody() {
    override fun contentType() = original.contentType()

    override fun contentLength() = original.contentLength()

    override fun writeTo(sink: BufferedSink) = original.writeTo(sink)

    override fun isOneShot(): Boolean = true
}
