package app.manyak

import android.app.Notification
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.auth.entity.SignInOutcome
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.core.navigation.PushEntry
import app.manyak.create.general.data.GeneralReviewWatch
import app.manyak.notification.data.PushNotificationTray
import app.manyak.notification.domain.PushRecipientGate
import app.manyak.notification.entity.PushMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModerationNotificationUiTest {
    @Test
    fun separateSubmissionsKeepServerTextAndHideBodyOnLockScreen() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val manager = context.getSystemService(NotificationManager::class.java)
            val first = payload("knk1520-local-1")
            val second = payload("knk1520-local-2")
            val ids = listOf(first, second).map { requireNotNull(PushMessage.from(it)).notificationId }
            val tray =
                PushNotificationTray(
                    context,
                    PushRecipientGate(NotificationSession(), NotificationProfile()),
                    GeneralReviewWatch(),
                )
            tray.ensureChannels()
            try {
                tray.show(first)
                tray.show(second)
                awaitNotifications(manager, ids)
                val notifications = manager.activeNotifications.filter { it.id in ids }
                assertEquals(2, notifications.size)
                assertNotEquals(
                    notifications[0].notification.contentIntent,
                    notifications[1].notification.contentIntent,
                )
                notifications.forEach { active ->
                    val notification = active.notification
                    assertEquals(PushNotificationTray.CHANNEL_SERVICE, notification.channelId)
                    assertEquals("검수를 통과했어요", notification.extras.getString(Notification.EXTRA_TITLE))
                    assertEquals(
                        "로컬 검증 스토리의 등록이 완료됐어요",
                        notification.extras.getString(Notification.EXTRA_TEXT),
                    )
                    assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
                    assertEquals("검수를 통과했어요", notification.publicVersion.extras.getString(Notification.EXTRA_TITLE))
                    assertNull(notification.publicVersion.extras.getString(Notification.EXTRA_TEXT))
                }
                tray.show(first)
                awaitNotifications(manager, ids)
                assertEquals(2, manager.activeNotifications.count { it.id in ids })
            } finally {
                ids.forEach(manager::cancel)
            }
        }

    @Test
    fun resultAlreadyShownOnScreenIsNotPostedAgain() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val manager = context.getSystemService(NotificationManager::class.java)
            val shown = payload("knk1521-shown")
            val other = payload("knk1521-other")
            val ids = listOf(shown, other).map { requireNotNull(PushMessage.from(it)).notificationId }
            val watch = GeneralReviewWatch().apply { shown("knk1521-shown", "APPROVED") }
            val tray =
                PushNotificationTray(
                    context,
                    PushRecipientGate(NotificationSession(), NotificationProfile()),
                    watch,
                )
            tray.ensureChannels()
            try {
                tray.show(shown)
                tray.show(other)
                awaitNotifications(manager, ids, expected = 1)
                assertEquals(listOf(ids[1]), manager.activeNotifications.map { it.id }.filter { it in ids })
            } finally {
                ids.forEach(manager::cancel)
            }
        }

    private fun awaitNotifications(
        manager: NotificationManager,
        ids: List<Int>,
        expected: Int = 2,
    ) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (manager.activeNotifications.count { it.id in ids } != expected && System.nanoTime() < deadline) {
            Thread.sleep(50)
        }
    }

    private fun payload(id: String) =
        mapOf(
            "type" to PushEntry.TYPE_STORY_MODERATION_COMPLETED,
            "submissionId" to id,
            "status" to "APPROVED",
            "recipientId" to "local-qa",
            "title" to "검수를 통과했어요",
            "body" to "로컬 검증 스토리의 등록이 완료됐어요",
            "deepLink" to "https://manyak.app/studio",
        )
}

private class NotificationSession : SessionRepository {
    override val sessionState = MutableStateFlow<SessionState>(SessionState.Member)
    override val signInInProgress = MutableStateFlow<AuthProvider?>(null)

    override suspend fun signIn(provider: AuthProvider): DomainResult<SignInOutcome> = error("Not used")

    override suspend fun signOut() = Unit

    override suspend fun withdraw(): DomainResult<Unit> = error("Not used")

    override suspend fun acknowledgeSessionEndNotice() = Unit
}

private class NotificationProfile : UserProfileRepository {
    override val profile =
        MutableStateFlow<UserProfile?>(
            UserProfile("local-qa", "검증", null, null, AccountStatus.ACTIVE, 0, false, emptyList()),
        )

    override suspend fun refresh() = DomainResult.Success(requireNotNull(profile.value))
}
