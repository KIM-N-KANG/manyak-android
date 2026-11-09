package app.manyak.create.presentation.state

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.domain.SimpleDraftStore
import app.manyak.create.domain.serverDraftLink
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.ServerDraftLink
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.StoryDraftSave

/**
 * 퍼널 세션 하나의 저장 위치와 서버 초안 기준 버전. 진입 판정에서 한 번 정하고, 세션 동안에는 로그인 상태가 흔들려도
 * 바꾸지 않는다. 서버 세션은 사용자 저장을 같은 ID 의 서버 초안에 쓰고, 기기 안 세션(서버 연동 전에 만든 초안)은
 * 진행 레코드에 쓴다. 호출부가 같은 초안의 쓰기를 직렬화한다.
 */
internal class SimpleDraftSession(
    private val local: PendingStoryCreationStore,
    private val server: SimpleDraftStore,
) {
    /** 진입 판정을 마쳤는지. 마치기 전에는 저장 위치를 모르므로 기기 안에 쓴다. */
    var resolved = false
        private set

    var onServer = false
        private set

    /** 다음 서버 저장의 기준 버전. 서버에 아직 없으면 null 이다. */
    private var version: Int? = null

    /** 서버 초안을 처음 저장한 시각. 진행 기록과 완성 요청이 이어받아 카드 순서와 날짜가 바뀌지 않는다. */
    var createdAt: Long? = null
        private set

    /** 서버 세션이 기기 안에 남긴 진행 기록(생성 중, 생성 결과)이 있는지. 서버 저장에 성공하면 지운다. */
    private var ownsLocalRecord = false

    /** 진입 판정이 복원한 키워드 입력. 키워드 화면이 한 번 꺼내 쓴다. */
    private var restoredKeyword: KeywordDraftSnapshot? = null

    /** 카드로 연 서버 초안이 추가 정보 단계였으면 그 스토리라인 순번. 스토리라인 화면이 한 번 꺼내 넘긴다. */
    private var resumeStorylineIndex: Int? = null

    /** 서버 세션의 진행 기록과 완성 요청에 붙이는 서버 초안. 기기 안 세션은 null 이다. */
    val link: ServerDraftLink? get() = if (onServer) ServerDraftLink(version) else null

    /**
     * 진입 판정. 기기 안 기록이 있으면 그 기록이 정본이고(서버 초안을 가리키면 서버 세션), 없으면 같은 ID 의 서버 초안을
     * 읽는다. 서버를 읽지 못하면 null 이고 판정은 끝나지 않는다. [resume]은 제작 탭 카드로 연 경우다.
     *
     * [preferServer]는 이 기기의 기록을 남긴 뒤 다른 곳에서 다시 저장해 따로 보이던 서버 초안 카드로 연 경우다. 이 세션의 진행
     * 기록이 같은 ID 의 기록을 덮지 않도록, 이 기기의 기록은 새 ID 의 기기 안 초안으로 떼어 낸 뒤 서버 초안을 연다.
     */
    suspend fun open(
        draftId: String,
        resume: Boolean,
        preferServer: Boolean = false,
    ): Opened? {
        if (preferServer && !local.detach(draftId)) return null
        val record = if (preferServer) null else local.read(draftId)
        val opened =
            if (record != null) {
                val link = record.serverDraftLink
                onServer = link != null
                version = link?.baseVersion
                ownsLocalRecord = onServer
                Opened(record, FunnelEntryOutcome.READY)
            } else {
                when (val result = server.open(draftId)) {
                    is DomainResult.Failure -> return null
                    is DomainResult.Success -> openServerDraft(result.value, resume)
                }
            }
        (opened.record as? PendingStoryCreation.KeywordDraft)?.let { restoredKeyword = it.snapshot }
        resolved = true
        return opened
    }

    private fun openServerDraft(
        opened: SimpleDraftOpen,
        resume: Boolean,
    ): Opened {
        onServer = true
        return when (opened) {
            is SimpleDraftOpen.Found -> {
                version = opened.version
                createdAt = opened.createdAt
                if (resume) {
                    resumeStorylineIndex =
                        (opened.record as? PendingStoryCreation.Draft)?.progress?.selectedStorylineIndex
                }
                Opened(opened.record, FunnelEntryOutcome.READY)
            }

            // 생성 결과를 잃고 남은 초안은 그 버전으로 덮어써 같은 ID 로 새로 시작한다.
            is SimpleDraftOpen.NotFound -> {
                version = opened.version
                Opened(null, if (resume) FunnelEntryOutcome.NOT_FOUND else FunnelEntryOutcome.READY)
            }
        }
    }

    fun takeRestoredKeyword(): KeywordDraftSnapshot? = restoredKeyword.also { restoredKeyword = null }

    fun takeResumeStorylineIndex(): Int? = resumeStorylineIndex.also { resumeStorylineIndex = null }

    /** 이탈 경고가 묻는 저장본. 서버 세션은 서버에 저장한 초안, 기기 안 세션은 기기 안 기록이다. */
    suspend fun hasSavedDraft(draftId: String): Boolean = (onServer && version != null) || local.read(draftId) != null

    /** 진행 기록을 기기 안에 남기거나 지웠다. 서버 세션이 남긴 기록은 다음 서버 저장이 지운다. */
    fun localRecordChanged(present: Boolean) {
        ownsLocalRecord = onServer && present
    }

    /** 충돌 확인에서 지금 내용으로 저장하기로 했다. 서버의 현재 버전을 다음 기준으로 쓴다. */
    fun overwrite(serverVersion: Int) {
        version = serverVersion
    }

    /** 키워드 입력을 저장한다. 서버 세션은 기기에 남기지 않고, 입력을 모두 지웠으면 저장본을 지운다. */
    suspend fun saveKeyword(
        draftId: String,
        snapshot: KeywordDraftSnapshot,
    ): StoryDraftSave =
        when {
            !onServer ->
                if (local.persistKeywordSnapshot(draftId, snapshot)) StoryDraftSave.Saved(0) else StoryDraftSave.Failed

            !snapshot.hasInput ->
                if (version != null && !server.delete(draftId)) {
                    StoryDraftSave.Failed
                } else {
                    version = null
                    StoryDraftSave.Saved(0)
                }

            else -> save(draftId, PendingStoryCreation.KeywordDraft(snapshot))
        }

    /** 스토리라인 초안을 서버에 쓴다. 성공하면 같은 제작이 서버 초안 한 장으로 보이도록 서버로 옮긴 진행 기록을 지운다. */
    suspend fun saveStory(
        draftId: String,
        record: PendingStoryCreation.Draft,
    ): StoryDraftSave =
        save(draftId, record).also {
            if (it is StoryDraftSave.Saved && ownsLocalRecord && local.clear(draftId)) ownsLocalRecord = false
        }

    private suspend fun save(
        draftId: String,
        record: PendingStoryCreation,
    ): StoryDraftSave =
        server.save(draftId, record, version).also {
            if (it is StoryDraftSave.Saved) {
                version = it.version
                createdAt = createdAt ?: it.createdAt
            }
        }

    fun reset() {
        resolved = false
        onServer = false
        version = null
        createdAt = null
        ownsLocalRecord = false
        restoredKeyword = null
        resumeStorylineIndex = null
    }

    data class Opened(
        val record: PendingStoryCreation?,
        val outcome: FunnelEntryOutcome,
    )
}

/** 같은 초안의 뒤 단계 생성·결과 레코드는 키워드 편집본보다 우선한다. */
private suspend fun PendingStoryCreationStore.persistKeywordSnapshot(
    draftId: String,
    snapshot: KeywordDraftSnapshot,
): Boolean {
    val current = read(draftId)
    if (current != null && current !is PendingStoryCreation.KeywordDraft) return false
    // 입력을 모두 지운 상태를 저장하면 남아 있던 저장본도 함께 사라져야 한다.
    if (!snapshot.hasInput) {
        return if (current is PendingStoryCreation.KeywordDraft) clear(draftId) else true
    }
    return write(draftId, PendingStoryCreation.KeywordDraft(snapshot))
}
