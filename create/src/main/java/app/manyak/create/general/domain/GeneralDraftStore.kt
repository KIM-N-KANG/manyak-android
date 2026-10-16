package app.manyak.create.general.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.entity.GeneralDraftOpen
import app.manyak.create.general.entity.GeneralDraftSave
import app.manyak.create.general.entity.GeneralStoredDraft
import app.manyak.create.general.entity.GeneralStoryForm
import kotlinx.coroutines.flow.Flow

/** 회원의 일반 제작 임시 저장본은 서버 초안이다. 서버 연동 전에 기기에 남은 저장본은 같은 ID로 옮긴다. */
interface GeneralDraftStore {
    /** 서버 초안과 기기 안 저장본. 같은 ID는 서버 쪽만 남긴다. 서버 목록을 받지 못했으면 기기 안 저장본만 있다. */
    val drafts: Flow<List<GeneralStoredDraft>>

    /** 서버 초안 목록을 다시 받고, 받았으면 기기 안 저장본을 서버로 옮긴다. */
    suspend fun refresh()

    suspend fun open(draftId: String): DomainResult<GeneralDraftOpen>

    suspend fun acceptedSubmissionId(draftId: String): String?

    /** 다른 곳에서 지워져 [baseVersion]이 거절되면 같은 ID로 새로 만들어 입력을 남긴다. */
    suspend fun save(
        draftId: String,
        form: GeneralStoryForm,
        baseVersion: Int?,
    ): GeneralDraftSave

    /** 접수한 초안을 기록해, 복원된 화면을 제출본으로 잇고 늦은 저장이 초안을 되살리지 못하게 한다. */
    suspend fun markAccepted(
        draftId: String,
        submissionId: String,
    ): Boolean

    suspend fun delete(draftId: String): Boolean
}
