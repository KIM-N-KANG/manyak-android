package app.manyak.create.general.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralImageKind
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoredDraft
import app.manyak.create.general.entity.GeneralStoryContent
import app.manyak.create.general.entity.GeneralStoryEditor
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralStoryPatch
import app.manyak.create.general.entity.GeneralStorySaveResult
import kotlinx.coroutines.flow.MutableStateFlow

internal open class GeneralEditorRepositoryFake : GeneralStoryRepository {
    var posts = 0
    var puts = 0
    var submitResult: suspend () -> DomainResult<GeneralStorySaveResult.Accepted> = {
        DomainResult.Success(GeneralStorySaveResult.Accepted("submission"))
    }
    var loadResult: suspend () -> DomainResult<GeneralStoryEditor> = { DomainResult.Failure(DomainError.Unknown) }
    var uploadResult: suspend (String) -> DomainResult<GeneralStoryImage> = {
        DomainResult.Failure(DomainError.Unknown)
    }
    val discarded = mutableListOf<GeneralStoryImage>()

    override suspend fun submit(content: GeneralStoryContent): DomainResult<GeneralStorySaveResult.Accepted> {
        posts++
        return submitResult()
    }

    override suspend fun submission(submissionId: String): DomainResult<GeneralStoryEditor> = loadResult()

    override suspend fun edit(storyId: String): DomainResult<GeneralStoryEditor> = loadResult()

    override suspend fun resubmit(
        submissionId: String,
        content: GeneralStoryContent,
    ): DomainResult<GeneralStorySaveResult.Accepted> {
        puts++
        return submitResult()
    }

    override suspend fun update(
        storyId: String,
        patch: GeneralStoryPatch,
    ): DomainResult<GeneralStorySaveResult> = DomainResult.Failure(DomainError.Unknown)

    override suspend fun deleteCover(storyId: String): DomainResult<Unit> = DomainResult.Success(Unit)

    override suspend fun uploadImage(
        uri: String,
        kind: GeneralImageKind,
        storyId: String?,
    ): DomainResult<GeneralStoryImage> = uploadResult(uri)

    override suspend fun discardImage(image: GeneralStoryImage) {
        discarded += image
    }
}

internal class GeneralDraftStoreFake : GeneralDraftStore {
    override val drafts = MutableStateFlow(emptyList<GeneralStoredDraft>())
    var form = validGeneralForm()
    var readFails = false
    var saveFails = false
    var acceptedSucceeds = true
    var saves = 0
    var acceptedWrites = 0
    var acceptedId: String? = null

    override suspend fun read(draftId: String): GeneralStoredDraft {
        if (readFails) error("database unavailable")
        return GeneralStoredDraft(draftId, form, 0)
    }

    override suspend fun acceptedSubmissionId(draftId: String): String? = acceptedId

    override suspend fun save(
        draftId: String,
        form: GeneralStoryForm,
    ): Boolean {
        saves++
        if (saveFails) error("database unavailable")
        this.form = form
        return true
    }

    override suspend fun markAccepted(
        draftId: String,
        submissionId: String,
    ): Boolean {
        acceptedWrites++
        if (acceptedSucceeds) acceptedId = submissionId
        return acceptedSucceeds
    }

    override suspend fun delete(draftId: String): Boolean = true
}

internal fun validGeneralForm() =
    GeneralStoryForm(
        title = "스토리 제목",
        oneLineIntro = "한 줄 소개",
        world = "세계관",
        progression = "전개 방식",
        protagonist = GeneralCharacter(name = "주인공", gender = GeneralGender.MALE, feature = "주인공 특징"),
        supporting = listOf(GeneralCharacter(name = "주변 인물", gender = GeneralGender.FEMALE)),
        startSettings =
            listOf(
                GeneralStartSetting(
                    name = "시작 상황",
                    prologue = "프롤로그",
                    situation = "상황 설명",
                    suggestedInputs = listOf("첫 입력", "두 번째", "세 번째"),
                ),
            ),
        genres = listOf("판타지"),
    )
