package app.manyak

import android.util.Log
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.core.navigation.NoticeDetailRoute
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.notification.inbox.domain.NotificationInboxRepository
import app.manyak.notification.inbox.entity.InboxNotification
import app.manyak.notification.inbox.entity.NotificationCategory
import app.manyak.notification.inbox.entity.NotificationPage
import app.manyak.notification.inbox.entity.NotificationUnreadCount
import app.manyak.notification.inbox.presentation.NotificationInboxScreen
import app.manyak.notification.inbox.presentation.NotificationInboxViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationInboxUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabsReadActionsAndRestoration() {
        val repository = InboxUiRepository()
        lateinit var vm: NotificationInboxViewModel
        compose.runOnUiThread { vm = NotificationInboxViewModel(repository) }
        val dark = mutableStateOf(false)
        var settingsOpened = false
        var destination: Any? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ManyakTheme(darkTheme = dark.value) {
                Surface {
                    NotificationInboxScreen({}, { settingsOpened = true }, { destination = it }, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText("새로운 기능을 만나 보세요", substring = true).assertIsDisplayed()
        compose.onNodeWithText("업데이트").assertIsDisplayed()
        capture("news-light")
        compose.onNodeWithText("활동").performClick()
        compose.onNodeWithText("스토리가 완성됐어요", substring = true).assertIsDisplayed()
        capture("activity-before-restore")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("스토리가 완성됐어요", substring = true).assertIsDisplayed()
        capture("activity-after-restore")
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("스토리가 완성됐어요", substring = true).assertIsDisplayed()
        capture("activity-dark")
        compose.onNodeWithText("모두 읽음").performClick()
        compose.waitUntil { repository.allRead }
        compose.onNodeWithContentDescription("안 읽은 알림 있음").assertDoesNotExist()
        capture("read-all")
        compose.onNodeWithContentDescription("알림 설정").performClick()
        compose.runOnIdle { assertEquals(true, settingsOpened) }
        compose.onNodeWithText("소식").performClick()
        compose.onNodeWithText("새로운 기능을 만나 보세요", substring = true).performClick()
        compose.runOnIdle { assertEquals(NoticeDetailRoute("notice"), destination) }
    }

    @Test fun loadingFailureRetryAndEmpty() {
        val firstResponse = CompletableDeferred<DomainResult<NotificationPage>>()
        var requests = 0
        val repository =
            object : NotificationInboxRepository by InboxUiRepository() {
                override suspend fun list(
                    category: NotificationCategory,
                    cursor: String?,
                ): DomainResult<NotificationPage> =
                    if (requests++ == 0) {
                        firstResponse.await()
                    } else {
                        DomainResult.Success(NotificationPage(emptyList(), null))
                    }
            }
        lateinit var vm: NotificationInboxViewModel
        compose.runOnUiThread { vm = NotificationInboxViewModel(repository) }
        compose.setContent {
            ManyakTheme {
                Surface { NotificationInboxScreen({}, {}, {}, viewModel = vm) }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("알림을 불러오는 중").assertIsDisplayed()
        capture("loading")
        compose.runOnIdle { firstResponse.complete(DomainResult.Failure(DomainError.Network)) }
        compose.onNodeWithText("알림을 불러오지 못했어요").assertIsDisplayed()
        capture("error")
        compose.onNodeWithText("다시 시도").performClick()
        compose.onNodeWithText("아직 받은 소식이 없어요").assertIsDisplayed()
        capture("empty")
        compose.runOnIdle { assertEquals(2, requests) }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureInbox") != "true") return
        compose.waitForIdle()
        Log.i("Knk1827QA", name)
        Thread.sleep(2_000)
    }
}

private class InboxUiRepository : NotificationInboxRepository {
    @Volatile var allRead = false

    override suspend fun list(
        category: NotificationCategory,
        cursor: String?,
    ): DomainResult<NotificationPage> {
        val news = category == NotificationCategory.NEWS
        return DomainResult.Success(
            NotificationPage(
                listOf(
                    InboxNotification(
                        id = category.name,
                        category = category,
                        type = if (news) "NOTICE" else "STORY_COMPLETED",
                        title = if (news) "새로운 기능을 만나 보세요" else "스토리가 완성됐어요",
                        body = if (news) "공지와 이벤트 소식을 알림에서 확인할 수 있어요" else "내가 만든 스토리를 지금 플레이해 보세요",
                        noticeId = if (news) "notice" else null,
                        storyId = if (news) null else "story",
                        noticeTag = if (news) "UPDATE" else null,
                        createdAtMillis = System.currentTimeMillis(),
                        isRead = allRead,
                    ),
                ),
                null,
            ),
        )
    }

    override suspend fun unreadCount() =
        DomainResult.Success(if (allRead) NotificationUnreadCount() else NotificationUnreadCount(2, 1, 1))

    override suspend fun read(id: String) = DomainResult.Success(Unit)

    override suspend fun readAll(): DomainResult<Unit> {
        allRead = true
        return DomainResult.Success(Unit)
    }
}
