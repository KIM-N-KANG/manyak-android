package app.manyak.create.domain

import app.manyak.create.entity.PendingStoryCreation
import kotlinx.coroutines.flow.Flow

/**
 * 진행 레코드 단일 슬롯. 로그아웃 시 전량 삭제되는 사용자 귀속 저장소이며,
 * 구현은 세션 종료 정리 계약에 참여해야 한다.
 */
interface PendingStoryCreationStore {
    /** 홈 배너가 관찰한다. 해석할 수 없는 레코드는 null 로 취급한다. */
    val record: Flow<PendingStoryCreation?>

    suspend fun read(): PendingStoryCreation?

    /** 레코드가 영속 저장소에 반영됐을 때만 true. */
    suspend fun write(record: PendingStoryCreation): Boolean

    /** 단일 슬롯이 비워졌을 때만 true. */
    suspend fun clear(): Boolean
}
