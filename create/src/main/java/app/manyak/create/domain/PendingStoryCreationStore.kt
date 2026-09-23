package app.manyak.create.domain

import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoredCreationDraft
import kotlinx.coroutines.flow.Flow

/**
 * 편집 초안 저장소. 초안은 퍼널 세션의 draftId 로 구분되어 여러 개가 공존한다. 회원별로 격리되어
 * 로그인한 회원의 행만 보이고, 로그아웃해도 지우지 않는다.
 */
interface PendingStoryCreationStore {
    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    suspend fun claimUnowned()

    /** 제작 탭 카드가 관찰한다. 처음 저장 시각 최신순이고, 해석할 수 없는 레코드는 빠진다. */
    val drafts: Flow<List<StoredCreationDraft>>

    suspend fun read(draftId: String): PendingStoryCreation?

    /** 레코드가 영속 저장소에 반영됐을 때만 true. 처음 쓰는 초안이면 지금이 처음 저장 시각이 된다. */
    suspend fun write(
        draftId: String,
        record: PendingStoryCreation,
    ): Boolean

    /** 초안이 비워졌을 때만 true. 다른 초안은 건드리지 않는다. */
    suspend fun clear(draftId: String): Boolean
}
