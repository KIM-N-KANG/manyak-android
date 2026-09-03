package app.manyak.feature.create

/** 퍼널 헤더의 임시 저장 버튼이 그리는 상태. */
enum class DraftSaveStatus {
    /** 기본 — "임시 저장" 라벨. */
    IDLE,

    /** 쓰기 진행 중 — 라벨 자리에 스피너를 띄우고 버튼을 잠근다. */
    SAVING,

    /** 쓰기 성공 — 체크와 "임시 저장됨"을 [DRAFT_SAVED_DISPLAY_MS] 동안 보여 주고 버튼을 잠근다. */
    SAVED,
}

/** 임시 저장 버튼을 그리고 이탈 경고를 가르는 데 필요한 전부. */
data class DraftSaveUiState(
    val status: DraftSaveStatus = DraftSaveStatus.IDLE,
    /** 저장할 내용이 있는지. 없으면 버튼을 잠근다. */
    val canSave: Boolean = false,
    /** 마지막 저장 이후 아직 반영하지 않은 변경. 이탈 경고를 띄울지 이 값으로 가른다. */
    val hasUnsavedChanges: Boolean = false,
)

/** 퍼널 이탈을 막고 띄우는 경고. 둘은 사라지는 대상이 달라 문구도 버튼도 다르다. */
enum class FunnelExitWarning {
    /** 저장한 스냅숏은 있지만 그 뒤의 편집이 남았다. */
    UNSAVED_CHANGES,

    /** 저장한 것도 저장할 것도 없다 — 생성 실패처럼 재개할 재료가 아예 없는 경우. */
    NOTHING_TO_PRESERVE,

    /** 저장한 스냅숏(또는 진행 중 레코드)만 남았다. 잃는 것은 없지만 닫기라는 사실만 확인받는다. */
    SAVED_DRAFT,
}

/** 저장 성공 표시를 유지하는 시간. 지나면 다시 "임시 저장"으로 돌아가 버튼이 풀린다. */
internal const val DRAFT_SAVED_DISPLAY_MS: Long = 2_000
