package app.manyak.create.general.data

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.create.general.data.api.GeneralAcceptedDto
import app.manyak.create.general.data.api.GeneralStoryApi
import app.manyak.create.general.data.api.GeneralStoryEditDto
import app.manyak.create.general.data.api.toEditor
import app.manyak.create.general.data.api.toRequest
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.entity.GeneralImageKind
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryEditor
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralStoryPatch
import app.manyak.create.general.entity.GeneralStorySaveResult
import app.manyak.network.data.api.apiCall
import app.manyak.network.data.api.emptyBodyApiCall
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

class GeneralStoryRepositoryImpl
    @Inject
    constructor(
        private val api: GeneralStoryApi,
        private val uploader: GeneralImageUploader,
        private val json: Json,
        private val imageFiles: GeneralImageFiles,
    ) : GeneralStoryRepository {
        private val changes =
            MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        override val submissionChanges = changes.asSharedFlow()

        override suspend fun submit(content: GeneralStoryContent): DomainResult<GeneralStorySaveResult.Accepted> =
            apiCall {
                api.submit(
                    content.toRequest(),
                )
            }.map { GeneralStorySaveResult.Accepted(it.submissionId) }.notifyChange()

        override suspend fun submission(submissionId: String): DomainResult<GeneralStoryEditor> =
            apiCall { api.submission(submissionId) }.map { it.toEditor() }

        override suspend fun edit(storyId: String): DomainResult<GeneralStoryEditor> =
            apiCall { api.edit(storyId) }.map { it.toEditor().copy(storyId = storyId) }

        override suspend fun resubmit(
            submissionId: String,
            content: GeneralStoryContent,
        ): DomainResult<GeneralStorySaveResult.Accepted> =
            apiCall {
                api.resubmit(
                    submissionId,
                    content.toRequest(),
                )
            }.map { GeneralStorySaveResult.Accepted(it.submissionId) }.notifyChange()

        override suspend fun update(
            storyId: String,
            patch: GeneralStoryPatch,
        ): DomainResult<GeneralStorySaveResult> {
            var status = 0
            return when (val result = apiCall { api.update(storyId, patch.toRequest()).also { status = it.code() } }) {
                is DomainResult.Failure -> result
                is DomainResult.Success ->
                    try {
                        val value =
                            if (status == HTTP_ACCEPTED) {
                                GeneralStorySaveResult.Accepted(
                                    json.decodeFromJsonElement<GeneralAcceptedDto>(result.value).submissionId,
                                )
                            } else {
                                GeneralStorySaveResult.Updated(
                                    json.decodeFromJsonElement<GeneralStoryEditDto>(result.value).toEditor(),
                                )
                            }
                        DomainResult.Success(value).notifyChange()
                    } catch (_: SerializationException) {
                        DomainResult.Failure(DomainError.Serialization)
                    }
            }
        }

        override suspend fun deleteCover(storyId: String): DomainResult<Unit> =
            emptyBodyApiCall { api.deleteCover(storyId) }.notifyChange()

        override suspend fun uploadImage(
            uri: String,
            kind: GeneralImageKind,
            storyId: String?,
        ): DomainResult<GeneralStoryImage> = uploader.upload(uri, kind, storyId)

        override suspend fun discardImage(image: GeneralStoryImage) = imageFiles.discard(image)

        private fun <T> DomainResult<T>.notifyChange(): DomainResult<T> =
            also {
                if (it is DomainResult.Success) changes.tryEmit(Unit)
            }

        private companion object {
            const val HTTP_ACCEPTED = 202
        }
    }
