package app.manyak.common.domain.story

/** 성공한 좋아요 수 변경을 이미 열려 있는 목록에 전달한다. */
fun interface StoryLikeUpdates {
    suspend fun updateLikeCount(
        storyId: String,
        likeCount: Long,
    )
}
