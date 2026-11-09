package app.manyak.create.domain

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.SimpleServerDraft
import app.manyak.create.entity.StoryDraftSave
import kotlinx.coroutines.flow.Flow

/** 회원 간편 제작의 서버 초안. 키워드 초안과 스토리라인 초안만 서버에 두고 진행 기록은 기기 안에 남는다. */
interface SimpleDraftStore {
    /** 마지막으로 받은 지금 계정의 서버 간편 제작 초안 카드. 목록은 일반 제작 초안과 함께 다시 받는다. */
    val drafts: Flow<List<SimpleServerDraft>>

    /** 형식을 읽을 수 없는 초안(다른 스키마 버전 등)은 실패다. 다시 저장하면 그 버전에만 있는 필드가 지워진다. */
    suspend fun open(draftId: String): DomainResult<SimpleDraftOpen>

    /** [record]는 키워드 초안 또는 스토리라인 초안이다. 다른 곳에서 지워져 [baseVersion]이 거절되면 같은 ID로 새로 만든다. */
    suspend fun save(
        draftId: String,
        record: PendingStoryCreation,
        baseVersion: Int?,
    ): StoryDraftSave

    suspend fun delete(draftId: String): Boolean
}
