package app.manyak.create.general.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.entity.GeneralImageKind
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryEditor
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralStoryPatch
import app.manyak.create.general.entity.GeneralStorySaveResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface GeneralStoryRepository {
    val submissionChanges: Flow<Unit> get() = emptyFlow()

    suspend fun submit(content: GeneralStoryContent): DomainResult<GeneralStorySaveResult.Accepted>

    suspend fun submission(submissionId: String): DomainResult<GeneralStoryEditor>

    suspend fun edit(storyId: String): DomainResult<GeneralStoryEditor>

    suspend fun resubmit(
        submissionId: String,
        content: GeneralStoryContent,
    ): DomainResult<GeneralStorySaveResult.Accepted>

    suspend fun update(
        storyId: String,
        patch: GeneralStoryPatch,
    ): DomainResult<GeneralStorySaveResult>

    suspend fun deleteCover(storyId: String): DomainResult<Unit>

    suspend fun uploadImage(
        uri: String,
        kind: GeneralImageKind,
        storyId: String? = null,
    ): DomainResult<GeneralStoryImage>

    suspend fun discardImage(image: GeneralStoryImage)
}
