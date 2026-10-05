package app.manyak.create.general.data

import android.content.Context
import android.net.Uri
import app.manyak.auth.domain.SessionGate
import app.manyak.common.data.di.IoDispatcher
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.create.general.data.api.GeneralImagePresignRequestDto
import app.manyak.create.general.data.api.GeneralImageUploadApi
import app.manyak.create.general.data.api.GeneralStoryApi
import app.manyak.create.general.entity.GeneralImageKind
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

class GeneralImageUploader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val api: GeneralStoryApi,
        private val uploadApi: GeneralImageUploadApi,
        private val files: GeneralImageFiles,
        private val profileRepository: UserProfileRepository,
        private val gate: SessionGate,
    ) {
        suspend fun upload(
            source: String,
            kind: GeneralImageKind,
            storyId: String?,
        ): DomainResult<GeneralStoryImage> {
            var image: GeneralStoryImage? = null
            var returned = false
            try {
                val result =
                    gate.withAuthWork(onBlocked = { DomainResult.Failure(DomainError.Unauthorized) }) { work ->
                        val ownerId =
                            profileRepository.profile.value?.id
                                ?: return@withAuthWork DomainResult.Failure(DomainError.Unauthorized)
                        when (val remote = uploadRemote(source, kind, storyId)) {
                            is DomainResult.Failure -> remote
                            is DomainResult.Success -> {
                                gate.commit(work) {
                                    persist(ownerId, remote.value).also {
                                        image = (it as? DomainResult.Success)?.value
                                    }
                                } ?: DomainResult.Failure(DomainError.Unauthorized)
                            }
                        }
                    }
                returned = result is DomainResult.Success
                return result
            } finally {
                if (!returned) {
                    // 인증 작업의 결과 전달이 취소돼도 이미 만든 로컬 파일은 정리한다.
                    withContext(NonCancellable) { image?.let { files.discard(it) } }
                }
            }
        }

        private suspend fun persist(
            ownerId: String,
            input: UploadedInput,
        ): DomainResult<GeneralStoryImage> =
            try {
                val file = files.persist(ownerId, input.bytes, input.contentType)
                DomainResult.Success(
                    GeneralStoryImage(
                        objectKey = input.objectKey,
                        previewUrl = Uri.fromFile(file).toString(),
                        localPath = file.absolutePath,
                    ),
                )
            } catch (_: IOException) {
                DomainResult.Failure(DomainError.Unknown)
            }

        private suspend fun uploadRemote(
            source: String,
            kind: GeneralImageKind,
            storyId: String?,
        ): DomainResult<UploadedInput> {
            val input = read(source) ?: return DomainResult.Failure(invalidImage())
            val request = GeneralImagePresignRequestDto(kind.name, input.contentType, input.bytes.size.toLong())
            val presign =
                apiCall {
                    if (storyId == null) api.presignDraft(request) else api.presignStory(storyId, request)
                }
            if (presign is DomainResult.Failure) return presign
            val signed = (presign as DomainResult.Success).value
            val result =
                emptyBodyApiCall {
                    uploadApi.upload(signed.uploadUrl, input.bytes.toRequestBody(input.contentType.toMediaType()))
                }
            return when (result) {
                is DomainResult.Success ->
                    DomainResult.Success(UploadedInput(signed.objectKey, input.bytes, input.contentType))
                is DomainResult.Failure -> result
            }
        }

        private suspend fun read(source: String): ImageInput? =
            withContext(ioDispatcher) {
                try {
                    val uri = Uri.parse(source)
                    val input =
                        if (uri.scheme == "content") {
                            context.contentResolver.openInputStream(uri)
                        } else {
                            File(if (uri.scheme == "file") uri.path.orEmpty() else source).inputStream()
                        }
                    val bytes = input?.use(::readImageBytes) ?: return@withContext null
                    if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@withContext null
                    val contentType = imageContentType(bytes) ?: return@withContext null
                    ImageInput(bytes, contentType)
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }

        private data class ImageInput(
            val bytes: ByteArray,
            val contentType: String,
        )

        private data class UploadedInput(
            val objectKey: String,
            val bytes: ByteArray,
            val contentType: String,
        )
    }

// 이미지 포맷의 고정 파일 시그니처를 바이트 단위로 비교한다.
@Suppress("MagicNumber")
internal fun imageContentType(bytes: ByteArray): String? =
    when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() ->
            "image/jpeg"
        bytes.size >= 8 &&
            bytes
                .copyOfRange(
                    0,
                    8,
                ).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) ->
            "image/png"
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
        else -> null
    }

private fun invalidImage(): DomainError =
    DomainError.Server(status = HTTP_BAD_REQUEST, code = "INVALID_IMAGE", requestId = null)

private const val HTTP_BAD_REQUEST = 400
private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

private fun readImageBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() <= MAX_IMAGE_BYTES) {
        val count = input.read(buffer, 0, minOf(buffer.size, MAX_IMAGE_BYTES + 1 - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
