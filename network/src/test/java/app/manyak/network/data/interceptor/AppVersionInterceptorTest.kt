package app.manyak.network.data.interceptor

import app.manyak.network.data.api.HEADER_APP_VERSION
import app.manyak.network.data.di.DataLayerConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionInterceptorTest {
    @Test
    fun `every request carries the build app version`() {
        val sent = mutableListOf<String?>()

        execute(sent, requestVersion = null)

        assertEquals(listOf("1.0.2"), sent)
    }

    @Test
    fun `an app version already on the request is replaced, not duplicated`() {
        val sent = mutableListOf<String?>()

        execute(sent, requestVersion = "0.0.1")

        assertEquals(listOf("1.0.2"), sent)
    }

    private fun execute(
        sent: MutableList<String?>,
        requestVersion: String?,
    ) {
        val request =
            Request
                .Builder()
                .url("https://fixture.invalid/any")
                .apply { requestVersion?.let { header(HEADER_APP_VERSION, it) } }
                .build()

        OkHttpClient
            .Builder()
            .addInterceptor(AppVersionInterceptor(config(appVersion = "1.0.2")))
            .addInterceptor { chain ->
                sent += chain.request().headers(HEADER_APP_VERSION).singleOrNull()
                respondOk(chain.request())
            }.build()
            .newCall(request)
            .execute()
            .close()
    }

    private fun config(appVersion: String) =
        DataLayerConfig(
            apiBaseUrl = "https://fixture.invalid/",
            isDebugBuild = false,
            appVersion = appVersion,
        )

    private fun respondOk(request: Request): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("fixture")
            .body("fixture".toResponseBody())
            .build()
}
