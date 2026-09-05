package app.manyak.common.domain.story

import app.manyak.common.entity.story.CreationProgressSummary
import kotlinx.coroutines.flow.Flow

interface CreationProgressAccess {
    val progress: Flow<CreationProgressSummary?>

    /** 기존 제작을 폐기한 다음 새 퍼널로 진입할 때 사용한다. */
    suspend fun discard(): Boolean
}
