package app.manyak.create.domain

import app.manyak.create.entity.PendingStoryCreation
import kotlinx.coroutines.flow.Flow

/**
 * 진행 레코드 단일 슬롯. 회원별로 격리되어 로그인한 회원의 행만 보이고, 로그아웃해도 지우지 않는다.
 */
interface PendingStoryCreationStore {
    /** 소유자를 모르는 이전 버전 행을 지금 회원의 것으로 넘긴다. */
    suspend fun claimUnowned()

    /** 홈 배너가 관찰한다. 해석할 수 없는 레코드는 null 로 취급한다. */
    val record: Flow<PendingStoryCreation?>

    suspend fun read(): PendingStoryCreation?

    /** 레코드가 영속 저장소에 반영됐을 때만 true. */
    suspend fun write(record: PendingStoryCreation): Boolean

    /** 단일 슬롯이 비워졌을 때만 true. */
    suspend fun clear(): Boolean
}
