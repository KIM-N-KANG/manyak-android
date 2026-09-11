package app.manyak.notification.entity

import app.manyak.core.navigation.PushEntry

/** 서버 data 전용 페이로드를 표시에 필요한 값으로 줄인 것. 값은 전부 문자열이다. */
data class PushMessage(
    val type: String,
    /** `type` 별 대상 식별자. 같은 대상의 재발송이 이전 알림을 교체하는 기준이기도 하다. */
    val targetId: String?,
    val recipientId: String?,
    val title: String?,
    val body: String?,
) {
    val entry: PushEntry get() = PushEntry(type = type, targetId = targetId, recipientId = recipientId)

    val isMarketing: Boolean
        get() = type == PushEntry.TYPE_ATTENDANCE_REMINDER || type == PushEntry.TYPE_PROMOTION

    companion object {
        /** `type` 이 없으면 표시할 수 없는 메시지다. */
        fun from(data: Map<String, String>): PushMessage? {
            val type = data["type"]?.takeIf { it.isNotBlank() } ?: return null
            val targetId =
                when (type) {
                    PushEntry.TYPE_STORY_COMPLETED -> data["storyId"]
                    PushEntry.TYPE_ATTENDANCE_REMINDER -> data["date"]
                    PushEntry.TYPE_PROMOTION -> data["campaignId"]
                    else -> null
                }
            return PushMessage(
                type = type,
                targetId = targetId,
                recipientId = data["recipientId"]?.takeIf { it.isNotBlank() },
                title = data["title"],
                body = data["body"],
            )
        }
    }
}
