package app.manyak.notification.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.manyak.common.domain.session.UserScopedStore
import app.manyak.core.navigation.PushEntry
import app.manyak.notification.R
import app.manyak.notification.domain.PushRecipientGate
import app.manyak.notification.entity.PushMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import app.manyak.designsystem.R as DesignsystemR

/**
 * 시스템 알림 트레이. 채널을 만들고, 수신자가 확인된 메시지만 띄우고, 세션이 끝나면 전부 걷는다.
 *
 * 표시된 알림은 사용자 귀속 데이터라 [UserScopedStore] 로 종료 정리에 참여한다 — 로그아웃·탈퇴·
 * 서버 강제 종료·재개가 모두 같은 단계를 지나므로 별도 정리 경로를 두지 않는다.
 */
@Singleton
class PushNotificationTray
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val gate: PushRecipientGate,
    ) : UserScopedStore {
        override val storeName: String = "notification-tray"

        private val manager get() = NotificationManagerCompat.from(context)

        /** 멱등이다. 채널을 지우거나 이름을 바꾸면 사용자가 채널에 내린 설정이 사라진다. */
        fun ensureChannels() {
            manager.createNotificationChannelsCompat(
                listOf(
                    channel(CHANNEL_SERVICE, NotificationManagerCompat.IMPORTANCE_HIGH)
                        .setName(context.getString(R.string.notification_settings_service))
                        .setDescription(context.getString(R.string.notification_settings_service_description))
                        .build(),
                    channel(CHANNEL_MARKETING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                        .setName(context.getString(R.string.notification_settings_marketing))
                        .setDescription(context.getString(R.string.notification_settings_marketing_description))
                        .build(),
                ),
            )
        }

        /** 수신자 확인·표시를 통틀어 [ADMIT_TIMEOUT_MILLIS] 안에 끝낸다. 넘기면 버린다. */
        suspend fun show(data: Map<String, String>) {
            val message = PushMessage.from(data) ?: return
            if (withTimeoutOrNull(ADMIT_TIMEOUT_MILLIS) { gate.admits(message.recipientId) } != true) return
            post(message)
        }

        override suspend fun clearUserData(): Boolean {
            manager.cancelAll()
            return true
        }

        private fun post(message: PushMessage) {
            val (title, body) = resolveText(message) ?: return
            // 권한이 없으면 OS 가 막을 뿐이지만, `notify` 호출 자체가 같은 함수 안의 권한 검사를 요구한다.
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != PackageManager.PERMISSION_GRANTED || !gate.isMemberNow()) return
            val channelId = if (message.isMarketing) CHANNEL_MARKETING else CHANNEL_SERVICE
            val id = (message.type + message.targetId.orEmpty()).hashCode()
            val notification =
                NotificationCompat
                    .Builder(context, channelId)
                    .setSmallIcon(DesignsystemR.drawable.ic_bell)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(message, id))
                    .apply {
                        // 스토리 제목은 사용자가 지은 것이라 잠긴 화면에는 제목 줄만 남긴다.
                        if (!message.isMarketing) {
                            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                            setPublicVersion(
                                NotificationCompat
                                    .Builder(context, channelId)
                                    .setSmallIcon(DesignsystemR.drawable.ic_bell)
                                    .setContentTitle(title)
                                    .build(),
                            )
                        }
                    }.build()
            manager.notify(id, notification)
        }

        /** 제목과 본문. 스토리 완성은 서버 `title` 이 스토리 제목이라 본문 자리에 놓고 제목은 앱이 붙인다. */
        private fun resolveText(message: PushMessage): Pair<String, String?>? =
            when (message.type) {
                PushEntry.TYPE_STORY_COMPLETED ->
                    context.getString(R.string.notification_story_completed_title) to message.title
                else -> message.title?.takeIf { it.isNotBlank() }?.let { it to message.body }
            }

        private fun contentIntent(
            message: PushMessage,
            requestCode: Int,
        ): PendingIntent? {
            // MainActivity 는 :app 이 소유하므로 런처 컴포넌트로 명시 인텐트를 만든다.
            val component =
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
            val intent = message.entry.writeTo(Intent().setComponent(component).addFlags(ENTRY_FLAGS))
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun channel(
            id: String,
            importance: Int,
        ) = NotificationChannelCompat.Builder(id, importance)

        companion object {
            const val CHANNEL_SERVICE = "service"
            const val CHANNEL_MARKETING = "marketing"
            private const val ADMIT_TIMEOUT_MILLIS = 5_000L

            // singleTop 인 MainActivity 가 실행 중이면 onNewIntent 로, 없으면 새로 만들어 같은 보류 경로로 들어간다.
            private const val ENTRY_FLAGS =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
