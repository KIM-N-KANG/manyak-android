package app.manyak.core.navigation

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import java.io.Serializable
import java.net.URI
import java.net.URISyntaxException

/**
 * 푸시 알림 탭이나 웹의 앱 열기 링크로 들어온 외부 진입. 알림·인텐트·저장 상태를 오가는 값이라 식별자 문자열만 담는다.
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
    /** 서버가 실은 이동 주소. 있으면 이 값이 목적지의 정본이고 [type] 매핑은 없을 때만 쓴다. */
    val deepLink: String? = null,
) : Serializable {
    /** 현재 회원 기준의 도착지. 홈은 [MainTabsRoute] 로 돌려줘 호출부가 "없음" 과 구분하지 않아도 된다. */
    fun routeFor(currentUserId: String): NavKey =
        when {
            // 앱 열기 링크는 특정 회원에게 보낸 것이 아니라서 수신자를 비교하지 않는다.
            type == TYPE_APP_LINK -> deepLink?.let(DeepLink::routeOf) ?: MainTabsRoute
            recipientId != currentUserId -> MainTabsRoute
            // 해석 실패를 type 매핑으로 되돌리지 않는다 — 서버가 목적지를 바꾼 뒤에도 구 매핑으로 가게 된다.
            deepLink != null -> DeepLink.routeOf(deepLink) ?: MainTabsRoute
            type == TYPE_STORY_COMPLETED && !targetId.isNullOrBlank() -> StoryDetailRoute(targetId)
            type == TYPE_STORY_MODERATION_COMPLETED && !targetId.isNullOrBlank() -> StudioRoute
            type == TYPE_ATTENDANCE_REMINDER -> MyCreditChargeRoute()
            else -> MainTabsRoute
        }

    fun writeTo(intent: Intent): Intent =
        intent
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_TARGET_ID, targetId)
            .putExtra(EXTRA_RECIPIENT_ID, recipientId)
            .putExtra(EXTRA_DEEP_LINK, deepLink)

    companion object {
        private const val serialVersionUID = 1L

        const val TYPE_STORY_MODERATION_COMPLETED = "STORY_MODERATION_COMPLETED"
        const val TYPE_STORY_COMPLETED = "STORY_COMPLETED"
        const val TYPE_ATTENDANCE_REMINDER = "ATTENDANCE_REMINDER"
        const val TYPE_PROMOTION = "PROMOTION"
        const val TYPE_APP_LINK = "APP_LINK"

        private const val APP_LINK_SCHEME = "manyak"
        private const val APP_LINK_HOST = "manyak.app"

        // 클래스 직렬화 대신 문자열 셋으로 싣는다 — 앱 업데이트 전에 만든 PendingIntent 도 읽혀야 한다.
        private const val EXTRA_TYPE = "app.manyak.push.type"
        private const val EXTRA_TARGET_ID = "app.manyak.push.targetId"
        private const val EXTRA_RECIPIENT_ID = "app.manyak.push.recipientId"
        private const val EXTRA_DEEP_LINK = "app.manyak.push.deepLink"

        /**
         * 웹 앱 안내 배너가 여는 전용 스킴 링크(`manyak://manyak.app/...`)를 진입으로 바꾼다. 경로는 같은 주소의 https
         * 링크로 옮겨 푸시와 같은 [DeepLink] 해석을 쓴다. 다른 스킴이나 호스트는 받지 않는다.
         */
        fun fromAppLink(url: String?): PushEntry? {
            val uri =
                try {
                    URI(url ?: return null)
                } catch (_: URISyntaxException) {
                    return null
                }
            if (uri.scheme != APP_LINK_SCHEME || uri.host?.lowercase() != APP_LINK_HOST) return null
            return PushEntry(
                type = TYPE_APP_LINK,
                targetId = null,
                recipientId = null,
                deepLink = URI("https", APP_LINK_HOST, uri.path.orEmpty(), null).toString(),
            )
        }

        /** 앱 열기 링크로 실행된 인텐트가 아니면 null. */
        fun readAppLinkFrom(intent: Intent?): PushEntry? =
            if (intent?.action == Intent.ACTION_VIEW) fromAppLink(intent.dataString) else null

        /** 푸시 진입이 아닌 보통 실행이면 null. */
        fun readFrom(intent: Intent?): PushEntry? {
            val type = intent?.getStringExtra(EXTRA_TYPE) ?: return null
            return PushEntry(
                type = type,
                targetId = intent.getStringExtra(EXTRA_TARGET_ID),
                recipientId = intent.getStringExtra(EXTRA_RECIPIENT_ID),
                deepLink = intent.getStringExtra(EXTRA_DEEP_LINK),
            )
        }
    }
}
