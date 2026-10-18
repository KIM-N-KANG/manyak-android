package app.manyak.create.keyword.presentation

import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.presentation.state.DRAFT_SAVE_LOCK_MS
import app.manyak.create.presentation.state.DraftSaveMessage
import app.manyak.create.presentation.state.StorylineGenerationStore
import app.manyak.create.presentation.state.failureMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 키워드 단계의 임시 저장. 임시 저장 버튼과 백그라운드 전환이 부르는 유일한 저장 경로다. 쓰기를 인텐트 처리 안에서
 * 기다리면 그동안 다음 입력이 막히므로 따로 띄우고, 쓰는 중에 다시 부르면 건너뛰지 않고 앞 저장 뒤에 이어 보낸다.
 * 서버 저장의 실패와 충돌은 버튼 저장에만 알린다.
 */
internal class KeywordDraftSaver(
    private val store: StorylineGenerationStore,
    private val scope: CoroutineScope,
    private val uiState: StateFlow<CreateKeywordUiState>,
    private val onEvent: suspend (CreateKeywordEvent) -> Unit,
    private val onEffect: suspend (CreateKeywordEffect) -> Unit,
    private val onSaved: () -> Unit,
) {
    private var saveJob: Job? = null
    private var lockJob: Job? = null

    /**
     * 마지막으로 저장한 스냅숏. 연타로 같은 내용을 다시 쓰지 않기 위한 장부다.
     * UiState 의 `savedSnapshot` 은 이벤트 채널을 거쳐 한 박자 늦어 판정 기준으로 쓸 수 없다.
     */
    private var persisted: KeywordDraftSnapshot? = null

    fun restored(snapshot: KeywordDraftSnapshot) {
        persisted = snapshot
    }

    /**
     * 복원이 화면에 반영될 때까지 기다린다 — 버튼은 복원 중에도 눌리므로, 기다리지 않으면 아직 비어 있는 상태를
     * 스냅숏해 저장해 둔 입력을 지운다. [overwriteVersion]은 충돌 확인에서 덮어쓰기로 한 서버의 현재 버전이다.
     */
    fun save(
        notify: Boolean,
        overwriteVersion: Int? = null,
    ) {
        val previous = saveJob?.takeIf { it.isActive }
        saveJob =
            scope.launch {
                previous?.join()
                val state = uiState.first { !it.isRestoring || it.entryFailed }
                if (state.isRestoring || state.isGeneratingStorylines) return@launch
                val snapshot = state.toKeywordSnapshot()
                // 이미 같은 스냅숏이 저장돼 있으면 쓰지 않고 성공으로 끝낸다. 누른 사람에게는 저장 토스트가 뜬다.
                if (snapshot != persisted || overwriteVersion != null) {
                    onEvent(CreateKeywordEvent.DraftSaveStarted)
                    val result = store.saveKeywordDraft(snapshot, overwriteVersion)
                    if (result !is StoryDraftSave.Saved) {
                        onEvent(CreateKeywordEvent.DraftSaveFinished(snapshot, saved = false))
                        if (notify) notifyFailure(result)
                        return@launch
                    }
                    persisted = snapshot
                    onSaved()
                    if (overwriteVersion != null) onEffect(CreateKeywordEffect.ShowDraftMessage(DraftSaveMessage.SAVED))
                }
                onEvent(CreateKeywordEvent.DraftSaveFinished(snapshot, saved = true))
                scheduleLockRelease()
            }
    }

    /** 저장하기는 충돌 응답의 서버 버전을 기준으로 지금 입력을 다시 보낸다. 닫기는 서버 내용을 그대로 둔다. */
    suspend fun resolveConflict(
        overwrite: Boolean,
        state: CreateKeywordUiState,
    ) {
        val version = state.draftConflictVersion ?: return
        onEvent(CreateKeywordEvent.DraftConflictChanged(null))
        if (overwrite) save(notify = true, overwriteVersion = version)
    }

    /** 저장은 취소하지 않고 끝날 때까지 기다린다 — 쓰기를 끊으면 무엇이 남았는지 알 수 없다. */
    suspend fun join() {
        saveJob?.join()
    }

    private suspend fun notifyFailure(result: StoryDraftSave) {
        if (result is StoryDraftSave.Conflict) {
            onEvent(CreateKeywordEvent.DraftConflictChanged(result.version))
        } else {
            result.failureMessage()?.let { onEffect(CreateKeywordEffect.ShowDraftMessage(it)) }
        }
    }

    private fun scheduleLockRelease() {
        lockJob?.cancel()
        lockJob =
            scope.launch {
                delay(DRAFT_SAVE_LOCK_MS)
                onEvent(CreateKeywordEvent.DraftSaveLockExpired)
            }
    }
}
