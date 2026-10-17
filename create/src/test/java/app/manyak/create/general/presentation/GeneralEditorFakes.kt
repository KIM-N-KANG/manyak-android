package app.manyak.create.general.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralDraftOpen
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
    val draftIds = mutableListOf<String?>()

    override suspend fun submit(
        content: GeneralStoryContent,
        draftId: String?,
    ): DomainResult<GeneralStorySaveResult.Accepted> {
        posts++
        draftIds += draftId
        return submitResult()
    }

    override suspend fun submission(submissionId: String): DomainResult<GeneralStoryEditor> = loadResult()

    override suspend fun edit(storyId: String): DomainResult<GeneralStoryEditor> = loadResult()

    override suspend fun resubmit(
        submissionId: String,
        content: GeneralStoryContent,
        draftId: String?,
    ): DomainResult<GeneralStorySaveResult.Accepted> {
        puts++
        draftIds += draftId
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
    var openResult: DomainResult<GeneralDraftOpen> = DomainResult.Success(GeneralDraftOpen.Server(form, 3))
    var saveFails = false

    /** 비어 있으면 저장마다 버전을 하나씩 올려 성공한다. */
    val saveResults = ArrayDeque<StoryDraftSave>()
    var acceptedSucceeds = true
    val baseVersions = mutableListOf<Int?>()
    val saves get() = baseVersions.size
    var acceptedWrites = 0
    var acceptedId: String? = null

    override suspend fun refresh() = Unit

    override suspend fun open(draftId: String): DomainResult<GeneralDraftOpen> = openResult

    override suspend fun acceptedSubmissionId(draftId: String): String? = acceptedId

    override suspend fun save(
        draftId: String,
        form: GeneralStoryForm,
        baseVersion: Int?,
    ): StoryDraftSave {
        baseVersions += baseVersion
        if (saveFails) error("network unavailable")
        this.form = form
        return saveResults.removeFirstOrNull() ?: StoryDraftSave.Saved((baseVersion ?: 0) + 1)
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
