package app.manyak.create.general.data

import android.content.Context
import app.manyak.common.data.di.IoDispatcher
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

class GeneralImageFiles
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun persist(
            ownerId: String,
            bytes: ByteArray,
            contentType: String,
        ): File {
            var created: File? = null
            var returned = false
            try {
                val file =
                    withContext(ioDispatcher) {
                        val directory = ownerDirectory(ownerId).apply { mkdirs() }
                        val extension = if (contentType == "image/jpeg") "jpg" else contentType.substringAfter('/')
                        File(directory, "${UUID.randomUUID()}.$extension").also {
                            created = it
                            it.writeBytes(bytes)
                        }
                    }
                returned = true
                return file
            } finally {
                if (!returned) {
                    // 파일 쓰기 뒤 호출 dispatcher로 복귀하기 전에 취소되면 호출자는 경로를 받지 못한다.
                    withContext(NonCancellable + ioDispatcher) { created?.delete() }
                }
            }
        }

        suspend fun removeUnused(
            ownerId: String,
            previous: GeneralStoryForm?,
            current: GeneralStoryForm? = null,
        ) {
            withContext(ioDispatcher) {
                try {
                    val directory = ownerDirectory(ownerId).canonicalFile
                    (previous.paths() - current.paths()).forEach { path ->
                        val file = File(path)
                        if (file.canonicalFile.parentFile == directory) file.delete()
                    }
                } catch (_: IOException) {
                    // 정리 실패가 이미 끝난 초안 저장과 검수 수락을 실패로 바꾸지는 않는다.
                }
            }
        }

        suspend fun discard(image: GeneralStoryImage) {
            val path = image.localPath ?: return
            withContext(ioDispatcher) {
                try {
                    val file = File(path).canonicalFile
                    val root = File(context.filesDir, "general-story-images").canonicalFile
                    if (file.parentFile?.parentFile == root) file.delete()
                } catch (_: IOException) {
                    // 다음 편집을 막지 않는 임시 이미지 정리다.
                }
            }
        }

        private fun ownerDirectory(ownerId: String): File {
            val digest = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray())
            val directory = digest.joinToString("") { "%02x".format(it) }
            return File(context.filesDir, "general-story-images/$directory")
        }
    }

private fun GeneralStoryForm?.paths(): Set<String> =
    this
        ?.let { form ->
            (
                listOfNotNull(form.cover?.localPath, form.protagonist.image?.localPath) +
                    form.supporting.mapNotNull { it.image?.localPath }
            ).toSet()
        }.orEmpty()
