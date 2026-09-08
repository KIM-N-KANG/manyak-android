package app.manyak.core.navigation

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import java.io.Serializable

/**
 * 푸시 알림 탭으로 들어온 외부 진입. 알림·인텐트·저장 상태를 오가는 값이라 식별자 문자열만 담는다.
 *
 * 서버 페이로드가 곧 라우트가 되지 않는다 — 알 수 없는 [type] 이나 빠진 식별자는 홈으로 떨어지고,
 * [recipientId] 가 현재 회원과 다르면 그 화면으로 들어가지 않는다.
 */
data class PushEntry(
    val type: String,
    /** `type` 별 대상 식별자 — 스토리 완성이면 `storyId`. 없으면 null. */
    val targetId: String?,
    /** 서버가 실은 수신 회원 공개 ID. 탭 시점에 현재 회원과 다시 비교한다. */
    val recipientId: String?,
) : Serializable {
    /** 현재 회원 기준의 도착지. 홈은 [MainTabsRoute] 로 돌려줘 호출부가 "없음" 과 구분하지 않아도 된다. */
    fun routeFor(currentUserId: String): NavKey =
        when {
            recipientId != currentUserId -> MainTabsRoute
            type == TYPE_STORY_COMPLETED && !targetId.isNullOrBlank() -> StoryDetailRoute(targetId)
            type == TYPE_ATTENDANCE_REMINDER -> MyCreditChargeRoute
            else -> MainTabsRoute
        }

    fun writeTo(intent: Intent): Intent =
        intent
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_TARGET_ID, targetId)
            .putExtra(EXTRA_RECIPIENT_ID, recipientId)

    companion object {
        private const val serialVersionUID = 1L

        const val TYPE_STORY_COMPLETED = "STORY_COMPLETED"
        const val TYPE_ATTENDANCE_REMINDER = "ATTENDANCE_REMINDER"
        const val TYPE_PROMOTION = "PROMOTION"

        // 클래스 직렬화 대신 문자열 셋으로 싣는다 — 앱 업데이트 전에 만든 PendingIntent 도 읽혀야 한다.
        private const val EXTRA_TYPE = "app.manyak.push.type"
        private const val EXTRA_TARGET_ID = "app.manyak.push.targetId"
        private const val EXTRA_RECIPIENT_ID = "app.manyak.push.recipientId"

        /** 푸시 진입이 아닌 보통 실행이면 null. */
        fun readFrom(intent: Intent?): PushEntry? {
            val type = intent?.getStringExtra(EXTRA_TYPE) ?: return null
            return PushEntry(
                type = type,
                targetId = intent.getStringExtra(EXTRA_TARGET_ID),
                recipientId = intent.getStringExtra(EXTRA_RECIPIENT_ID),
            )
        }
    }
}
