package app.manyak.create.general.data

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.auth.domain.SessionGate
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.create.general.data.api.GeneralAcceptedDto
import app.manyak.create.general.data.api.GeneralImagePresignDto
import app.manyak.create.general.data.api.GeneralImagePresignRequestDto
import app.manyak.create.general.data.api.GeneralImageUploadApi
import app.manyak.create.general.data.api.GeneralStoryApi
import app.manyak.create.general.data.api.GeneralStoryEditDto
import app.manyak.create.general.data.api.GeneralStoryPatchDto
import app.manyak.create.general.data.api.GeneralStoryRequestDto
import app.manyak.create.general.data.api.GeneralSubmissionDto
import app.manyak.create.general.entity.GeneralImageKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.util.UUID
import kotlin.coroutines.CoroutineContext

class GeneralImageCancellationTest {
    @Test
    fun cancelledFileResultRemovesAlreadyWrittenImage() {
        val fixture = ImageCancellationFixture()
        var returned = false
        try {
            val job =
                fixture.scope.launch {
                    fixture.files.persist("owner", byteArrayOf(1, 2, 3), "image/png")
                    returned = true
                }
            fixture.caller.runNext()
            fixture.io.runNext()
            assertEquals(1, fixture.imageCount())

            job.cancel()
            fixture.drain()

            assertTrue(job.isCompleted)
            assertFalse(returned)
            assertEquals(0, fixture.imageCount())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancelledAuthResultRemovesImageBeforeCallerReceivesIt() {
        val fixture = ImageCancellationFixture()
        var returned = false
        try {
            val source = File(fixture.root, "source.jpg").apply { writeBytes(jpegHeader) }
            val uploader =
                GeneralImageUploader(
                    fixture.context,
                    fixture.io,
                    SuccessfulPresignApi(),
                    SuccessfulImageUploadApi(),
                    fixture.files,
                    ImageTestProfileRepository(),
                    SessionGate(),
                )
            val job =
                fixture.scope.launch {
                    uploader.upload(source.absolutePath, GeneralImageKind.COVER, null)
                    returned = true
                }
            fixture.caller.runNext()
            fixture.caller.runNext()
            fixture.io.runNext()
            fixture.caller.runNext()
            fixture.io.runNext()
            fixture.caller.runNext()
            assertEquals(1, fixture.imageCount())

            job.cancel()
            fixture.drain()

            assertTrue(job.isCompleted)
            assertFalse(returned)
            assertEquals(0, fixture.imageCount())
        } finally {
            fixture.close()
        }
    }
}

private class ImageCancellationFixture {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val root = File(targetContext.cacheDir, "image-cancellation-${UUID.randomUUID()}").apply { mkdirs() }
    val context =
        object : ContextWrapper(targetContext) {
            override fun getFilesDir(): File = root
        }
    val caller = QueuedImageDispatcher()
    val io = QueuedImageDispatcher()
    val scope = CoroutineScope(SupervisorJob() + caller)
    val files = GeneralImageFiles(context, io)

    fun imageCount(): Int = File(root, "general-story-images").walkTopDown().count { it.isFile }

    fun drain() {
        while (caller.hasNext || io.hasNext) {
            if (caller.hasNext) caller.runNext()
            if (io.hasNext) io.runNext()
        }
    }

    fun close() {
        scope.cancel()
        drain()
        root.deleteRecursively()
    }
}

private class QueuedImageDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    val hasNext: Boolean get() = queue.isNotEmpty()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        queue.addLast(block)
    }

    fun runNext() = queue.removeFirst().run()
}

private class ImageTestProfileRepository : UserProfileRepository {
    override val profile =
        MutableStateFlow<UserProfile?>(
            UserProfile("owner", "테스트", null, null, AccountStatus.ACTIVE, 0, false, emptyList()),
        )

    override suspend fun refresh(): DomainResult<UserProfile> = DomainResult.Success(checkNotNull(profile.value))
}

private class SuccessfulImageUploadApi : GeneralImageUploadApi {
    override suspend fun upload(
        url: String,
        body: RequestBody,
    ): Response<Unit> = Response.success(Unit)
}

private class SuccessfulPresignApi : GeneralStoryApi {
    override suspend fun presignDraft(request: GeneralImagePresignRequestDto): Response<GeneralImagePresignDto> =
        Response.success(GeneralImagePresignDto("https://storage.test/image.jpg", "uploaded/image.jpg", 600))

    override suspend fun presignStory(
        storyId: String,
        request: GeneralImagePresignRequestDto,
    ): Response<GeneralImagePresignDto> = error("Unexpected API call")

    override suspend fun submit(request: GeneralStoryRequestDto): Response<GeneralAcceptedDto> =
        error("Unexpected API call")

    override suspend fun submission(submissionId: String): Response<GeneralSubmissionDto> = error("Unexpected API call")

    override suspend fun resubmit(
        submissionId: String,
        request: GeneralStoryRequestDto,
    ): Response<GeneralAcceptedDto> = error("Unexpected API call")

    override suspend fun edit(storyId: String): Response<GeneralStoryEditDto> = error("Unexpected API call")

    override suspend fun update(
        storyId: String,
        request: GeneralStoryPatchDto,
    ): Response<JsonObject> = error("Unexpected API call")

    override suspend fun deleteCover(storyId: String): Response<Unit> = error("Unexpected API call")
}

private val jpegHeader = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
