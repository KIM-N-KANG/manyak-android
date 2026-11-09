package app.manyak.notification.inbox.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.core.navigation.NoticeDetailRoute
import app.manyak.notification.inbox.domain.NotificationInboxRepository
import app.manyak.notification.inbox.entity.InboxNotification
import app.manyak.notification.inbox.entity.NotificationCategory
import app.manyak.notification.inbox.entity.NotificationPage
import app.manyak.notification.inbox.entity.NotificationUnreadCount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationInboxViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `첫 조회 실패는 재시도하고 새로고침 실패는 목록을 유지한다`() =
        runTest {
            val repo = FakeInboxRepository()
            repo.failFirst = true
            val vm = NotificationInboxViewModel(repo)
            advanceUntilIdle()
            assertEquals(DomainError.Network, vm.uiState.value.page.error)
            repo.failFirst = false
            vm.onIntent(NotificationInboxIntent.Refresh)
            advanceUntilIdle()
            assertEquals(
                "news",
                vm.uiState.value.page.items
                    .single()
                    .id,
            )
            repo.failFirst = true
            vm.onIntent(NotificationInboxIntent.Refresh)
            advanceUntilIdle()
            assertEquals(
                "news",
                vm.uiState.value.page.items
                    .single()
                    .id,
            )
            assertNull(vm.uiState.value.page.error)
            assertEquals(false, vm.uiState.value.page.isLoading)
        }

    @Test fun `탭마다 커서를 유지하고 추가 실패 뒤 같은 커서로 재시도한다`() =
        runTest {
            val repo = FakeInboxRepository()
            val vm = NotificationInboxViewModel(repo)
            advanceUntilIdle()
            repo.failMore = true
            vm.onIntent(NotificationInboxIntent.LoadMore)
            advanceUntilIdle()
            assertEquals(
                listOf("news"),
                vm.uiState.value.page.items
                    .map { it.id },
            )
            assertEquals(DomainError.Network, vm.uiState.value.page.error)
            repo.failMore = false
            vm.onIntent(NotificationInboxIntent.LoadMore)
            advanceUntilIdle()
            assertEquals(
                listOf("news", "more"),
                vm.uiState.value.page.items
                    .map { it.id },
            )
            assertNull(vm.uiState.value.page.nextCursor)
            vm.onIntent(NotificationInboxIntent.Select(NotificationCategory.ACTIVITY))
            advanceUntilIdle()
            assertEquals(
                "activity",
                vm.uiState.value.page.items
                    .single()
                    .id,
            )
            vm.onIntent(NotificationInboxIntent.Select(NotificationCategory.NEWS))
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.page.items.size)
            assertEquals(
                listOf(null, "cursor", "cursor"),
                repo.cursors
                    .filter {
                        it.first == NotificationCategory.NEWS
                    }.map { it.second },
            )
        }

    @Test fun `읽음 실패도 이동하며 중복 요청은 막는다`() =
        runTest {
            val repo = FakeInboxRepository()
            val vm = NotificationInboxViewModel(repo)
            val effects = mutableListOf<NotificationInboxEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEffect.collect { effects += it } }
            advanceUntilIdle()
            val item =
                vm.uiState.value.page.items
                    .single()
            repo.readGate = CompletableDeferred()
            vm.onIntent(NotificationInboxIntent.Open(item))
            vm.onIntent(NotificationInboxIntent.Open(item))
            advanceUntilIdle()
            assertTrue(
                vm.uiState.value.page.items
                    .single()
                    .isRead,
            )
            assertEquals(1, repo.readCount)
            assertTrue(effects.isEmpty())
            repo.readGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(NotificationInboxEffect.Navigate(NoticeDetailRoute("notice"))), effects)
            vm.onIntent(
                NotificationInboxIntent.Open(
                    vm.uiState.value.page.items
                        .single(),
                ),
            )
            advanceUntilIdle()
            assertEquals(1, repo.readCount)
        }

    @Test fun `모두 읽음 실패는 두 탭을 복원하고 요청 중 바꾼 탭은 유지한다`() =
        runTest {
            val repo = FakeInboxRepository()
            val vm = NotificationInboxViewModel(repo)
            val effects = mutableListOf<NotificationInboxEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEffect.collect { effects += it } }
            advanceUntilIdle()
            vm.onIntent(NotificationInboxIntent.Select(NotificationCategory.ACTIVITY))
            advanceUntilIdle()
            repo.allGate = CompletableDeferred()
            vm.onIntent(NotificationInboxIntent.ReadAll)
            advanceUntilIdle()
            assertEquals(0L, vm.uiState.value.unread.total)
            assertTrue(
                vm.uiState.value.pages.values
                    .all { p -> p.items.all { it.isRead } },
            )
            vm.onIntent(NotificationInboxIntent.Select(NotificationCategory.NEWS))
            vm.onIntent(NotificationInboxIntent.ReadAll)
            advanceUntilIdle()
            repo.allGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(NotificationCategory.NEWS, vm.uiState.value.category)
            assertTrue(
                vm.uiState.value.pages.values
                    .all { p -> p.items.none { it.isRead } },
            )
            assertEquals(2L, vm.uiState.value.unread.total)
            assertEquals(1, repo.allCount)
            assertTrue(effects.contains(NotificationInboxEffect.ReadAllFailed))
        }

    @Test fun `이전 탭의 늦은 응답이 선택한 탭을 바꾸지 않는다`() =
        runTest {
            val repo = FakeInboxRepository()
            repo.listGate = CompletableDeferred()
            val vm = NotificationInboxViewModel(repo)
            advanceUntilIdle()
            vm.onIntent(NotificationInboxIntent.Select(NotificationCategory.ACTIVITY))
            advanceUntilIdle()
            assertEquals(NotificationCategory.ACTIVITY, vm.uiState.value.category)
            repo.listGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                "activity",
                vm.uiState.value.page.items
                    .single()
                    .id,
            )
            assertEquals(
                "news",
                vm.uiState.value.pages[NotificationCategory.NEWS]!!
                    .items
                    .single()
                    .id,
            )
        }
}

private class FakeInboxRepository : NotificationInboxRepository {
    var failMore = false
    var failFirst = false
    var listGate: CompletableDeferred<Unit>? = null
    var readGate: CompletableDeferred<Unit>? = null
    var allGate: CompletableDeferred<Unit>? = null
    var readCount = 0
    var allCount = 0
    val cursors = mutableListOf<Pair<NotificationCategory, String?>>()

    override suspend fun list(
        category: NotificationCategory,
        cursor: String?,
    ): DomainResult<NotificationPage> {
        cursors += category to cursor
        listGate?.await()
        if (cursor == null && failFirst) return DomainResult.Failure(DomainError.Network)
        if (cursor != null && failMore) return DomainResult.Failure(DomainError.Network)
        val id =
            if (cursor != null) {
                "more"
            } else if (category == NotificationCategory.NEWS) {
                "news"
            } else {
                "activity"
            }
        return DomainResult.Success(
            NotificationPage(
                listOf(InboxNotification(id, category, "NOTICE", id, noticeId = "notice")),
                if (cursor ==
                    null
                ) {
                    "cursor"
                } else {
                    null
                },
            ),
        )
    }

    override suspend fun unreadCount() = DomainResult.Success(NotificationUnreadCount(2, 1, 1))

    override suspend fun read(id: String): DomainResult<Unit> {
        readCount++
        readGate?.await()
        return DomainResult.Failure(DomainError.Network)
    }

    override suspend fun readAll(): DomainResult<Unit> {
        allCount++
        allGate?.await()
        return DomainResult.Failure(DomainError.Network)
    }
}
