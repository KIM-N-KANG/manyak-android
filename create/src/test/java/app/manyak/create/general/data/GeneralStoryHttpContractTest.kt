package app.manyak.create.general.data

import app.manyak.create.general.data.api.GeneralImagePresignRequestDto
import app.manyak.create.general.data.api.GeneralSettingsDto
import app.manyak.create.general.data.api.GeneralStoryPatchDto
import app.manyak.create.general.data.api.GeneralStoryRequestDto
import app.manyak.create.general.data.di.GeneralStoryModule
import app.manyak.network.data.di.DataLayerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralStoryHttpContractTest {
    @Test(timeout = 10_000)
    fun `일반 제작 모든 경로는 BASE_URL의 API 접두어를 한 번만 사용한다`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                val api = GeneralStoryModule.provideGeneralStoryApi(OkHttpClient(), server.config(), json)

                suspend fun expect(
                    method: String,
                    path: String,
                    call: suspend () -> Unit,
                ) {
                    server.enqueue(MockResponse(code = 400))
                    call()
                    val request = server.takeRequest()
                    assertEquals(method, request.method)
                    assertEquals("/api/v1/$path", request.url.encodedPath)
                }
                expect("POST", "stories/general") { api.submit(request()) }
                expect("GET", "stories/submissions/submission-id") { api.submission("submission-id") }
                expect("PUT", "stories/submissions/submission-id") { api.resubmit("submission-id", request()) }
                expect("GET", "stories/story-id/edit") { api.edit("story-id") }
                expect(
                    "PATCH",
                    "stories/story-id",
                ) { api.update("story-id", GeneralStoryPatchDto(visibility = "PUBLIC")) }
                expect("DELETE", "stories/story-id/thumbnail") { api.deleteCover("story-id") }
                val image = GeneralImagePresignRequestDto("COVER", "image/png", 3)
                expect("POST", "stories/images/presign") { api.presignDraft(image) }
                expect("POST", "stories/story-id/images/presign") { api.presignStory("story-id", image) }
            } finally {
                server.close()
            }
        }

    @Test(timeout = 10_000)
    fun `503의 즉시 재시도 헤더가 있어도 검수 제출을 반복하지 않는다`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(503)
                        .addHeader("Retry-After", "0")
                        .build(),
                )
                server.enqueue(MockResponse(code = 202, body = """{"submissionId":"duplicate","status":"PENDING"}"""))
                val api = GeneralStoryModule.provideGeneralStoryApi(OkHttpClient(), server.config(), json)
                assertEquals(503, api.submit(request()).code())
                assertEquals(1, server.requestCount)
            } finally {
                server.close()
            }
        }

    @Test(timeout = 10_000)
    fun `서명 URL 업로드는 정확한 형식과 길이만 보내고 앱 인증 헤더를 보내지 않는다`() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                server.enqueue(MockResponse(code = 204))
                val api = GeneralStoryModule.provideGeneralImageUploadApi(server.config(), json)
                val bytes = byteArrayOf(1, 2, 3)
                val url = server.url("/storage/image.png?signature=test").toString()
                assertTrue(api.upload(url, bytes.toRequestBody("image/png".toMediaType())).isSuccessful)
                val request = server.takeRequest()
                assertEquals("PUT", request.method)
                assertEquals("/storage/image.png", request.url.encodedPath)
                assertEquals("signature=test", request.url.encodedQuery)
                assertEquals("image/png", request.headers["Content-Type"])
                assertEquals("3", request.headers["Content-Length"])
                assertNull(request.headers["Authorization"])
                assertNull(request.headers["X-Manyak-Device-Id"])
                assertNull(request.headers["X-Manyak-Session-Id"])
                assertTrue(bytes.contentEquals(request.body!!.toByteArray()))
            } finally {
                server.close()
            }
        }
}

private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private fun MockWebServer.config() = DataLayerConfig(url("/api/v1/").toString(), false, "test", "https://example.test")

private fun request() =
    GeneralStoryRequestDto(
        title = "테스트 제목",
        oneLineIntro = "테스트 소개",
        description = null,
        genres = listOf("판타지"),
        storySettings = GeneralSettingsDto("세계", "인물", "주인공", "규칙"),
        startSettings = emptyList(),
        mainEvents = emptyList(),
        visibility = "PRIVATE",
        thumbnailObjectKey = null,
        characters = emptyList(),
    )
