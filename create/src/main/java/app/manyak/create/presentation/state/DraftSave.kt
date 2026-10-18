package app.manyak.create.presentation.state

import app.manyak.create.entity.StoryDraftSave

/** 퍼널 헤더의 임시 저장 버튼이 그리는 상태. */
enum class DraftSaveStatus {
    /** 기본 — "임시 저장" 라벨. */
    IDLE,

    /** 쓰기 진행 중 — 라벨 자리에 스피너를 띄우고 버튼을 잠근다. */
    SAVING,

    /** 쓰기 성공 — [DRAFT_SAVE_LOCK_MS] 동안 버튼을 잠근다. 그 사이 편집해도 풀지 않는다. */
    SAVED,
}

/** 임시 저장 버튼을 그리고 이탈 경고를 가르는 데 필요한 전부. */
data class DraftSaveUiState(
    val status: DraftSaveStatus = DraftSaveStatus.IDLE,
    /** 저장할 내용이 있는지. 없으면 버튼을 잠근다. */
    val canSave: Boolean = false,
    /** 마지막 저장 이후 아직 반영하지 않은 변경. 이탈 경고를 띄울지 이 값으로 가른다. */
    val hasUnsavedChanges: Boolean = false,
    /** 서버 저장이 충돌한 서버의 현재 버전. 있으면 화면이 지금 내용으로 덮어쓸지 묻는다. */
    val conflictVersion: Int? = null,
)

/** 서버 임시 저장의 안내. 성공은 헤더 버튼이 알리므로 충돌 뒤 덮어쓰기의 성공만 따로 싣는다. */
enum class DraftSaveMessage {
    SAVED,
    LIMIT,
    TOO_LARGE,
    FAILED,
}

/** 충돌은 다이얼로그로 묻고 성공은 안내하지 않으므로 null 이다. */
internal fun StoryDraftSave.failureMessage(): DraftSaveMessage? =
    when (this) {
        is StoryDraftSave.Saved, is StoryDraftSave.Conflict -> null
        StoryDraftSave.LimitExceeded -> DraftSaveMessage.LIMIT
        StoryDraftSave.TooLarge -> DraftSaveMessage.TOO_LARGE
        StoryDraftSave.Failed -> DraftSaveMessage.FAILED
    }

/** 퍼널 이탈을 막고 띄우는 경고. 잃을 것도 남길 것도 없으면 경고 없이 나간다. */
enum class FunnelExitWarning {
    /** 저장본 없이 입력한 내용이 사라진다. */
    UNSAVED_INPUT,

    /** 저장한 스냅숏은 있지만 그 뒤의 편집이 남았다. */
    UNSAVED_CHANGES,

    /** 저장한 스냅숏(또는 진행 중 레코드)만 남았다. 잃는 것은 없지만 닫기라는 사실만 확인받는다. */
    SAVED_DRAFT,
}

/** 저장 성공 뒤 버튼을 잠가 두는 시간. 저장 토스트가 떠 있는 동안 같은 저장을 거듭하지 않게 한다. */
internal const val DRAFT_SAVE_LOCK_MS: Long = 2_000
