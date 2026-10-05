package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralStoredDraft
import app.manyak.create.general.entity.GeneralStoryForm
import kotlinx.coroutines.flow.Flow

interface GeneralDraftStore {
    val drafts: Flow<List<GeneralStoredDraft>>

    suspend fun read(draftId: String): GeneralStoredDraft?

    suspend fun acceptedSubmissionId(draftId: String): String?

    suspend fun save(
        draftId: String,
        form: GeneralStoryForm,
    ): Boolean

    /** 수락된 초안의 입력을 지우고, 늦은 저장이 초안을 되살리지 못하도록 수락 식별자를 남긴다. */
    suspend fun markAccepted(
        draftId: String,
        submissionId: String,
    ): Boolean

    suspend fun delete(draftId: String): Boolean
}
