package app.manyak.create.general.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.error.map
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.entity.GeneralDraftOpen
import app.manyak.create.general.entity.GeneralStoryForm

/**
 * 한 제작 화면이 쓰는 서버 초안. 저장은 앞 응답의 버전을 다음 기준으로 삼아야 해서 버전을 화면 수명 동안 들고 있고,
 * 서버로 옮기지 못한 기기 안 저장본을 열었으면 그 폼을 기억해 이미지 파일을 지우지 않게 한다.
 */
internal class GeneralDraftSession(
    private val store: GeneralDraftStore,
    private val draftId: String,
) {
    /** 다음 저장의 기준 버전. 새 초안이면 null 이다. */
    var version: Int? = null

    /** 아직 기기에 남은 저장본. 서버에 처음 저장하면 기기에서 지워지므로 null 로 돌린다. */
    private var localForm: GeneralStoryForm? = null

    /** 기기 안 저장본이 참조하는 이미지 파일. 화면을 닫아도 지우지 않는다. */
    val localImagePaths: Set<String> get() =
        localForm
            ?.localImages()
            .orEmpty()
            .mapNotNull { it.localPath }
            .toSet()

    /** 저장본이 없으면(다른 곳에서 등록하거나 지운 경우 포함) null 이다. */
    suspend fun open(): DomainResult<GeneralStoryForm?> =
        store.open(draftId).map { opened ->
            when (opened) {
                is GeneralDraftOpen.Local -> opened.form.also { localForm = it }
                is GeneralDraftOpen.Server -> opened.form.also { version = opened.version }
                GeneralDraftOpen.NotFound -> null
            }
        }

    suspend fun save(form: GeneralStoryForm): StoryDraftSave =
        store.save(draftId, form, version).also {
            if (it is StoryDraftSave.Saved) {
                version = it.version
                localForm = null
            }
        }
}

/** 충돌은 다이얼로그로 묻는다. 알리지 않는 저장이면 null 이다. */
internal fun StoryDraftSave.conflictVersion(notify: Boolean): Int? =
    (this as? StoryDraftSave.Conflict)?.version?.takeIf { notify }

/** 성공은 헤더 버튼이 알려 [announceSaved]일 때만 직접 알린다. 실패는 [notify]일 때만 알린다. */
internal fun StoryDraftSave.toastMessage(
    notify: Boolean,
    announceSaved: Boolean,
): GeneralEditorMessage? =
    when (this) {
        is StoryDraftSave.Saved -> GeneralEditorMessage.DRAFT_SAVED.takeIf { announceSaved }
        is StoryDraftSave.Conflict -> null
        StoryDraftSave.LimitExceeded -> GeneralEditorMessage.DRAFT_LIMIT.takeIf { notify }
        StoryDraftSave.TooLarge -> GeneralEditorMessage.DRAFT_TOO_LARGE.takeIf { notify }
        StoryDraftSave.Failed -> GeneralEditorMessage.DRAFT_FAILED.takeIf { notify }
    }
