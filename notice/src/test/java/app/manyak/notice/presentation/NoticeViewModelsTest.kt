package app.manyak.notice.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.notice.entity.NoticeDetail
import app.manyak.notice.entity.NoticePage
import app.manyak.notice.entity.NoticeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 안 읽음 판정의 갱신, 커서 이어 붙이기와 실패 자리, 상세 읽음 기록과 404 를 고정한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoticeViewModelsTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000
    private val recent = NoticeSummary("recent", "새 공지", now - day)
    private val old = NoticeSummary("old", "오래된 공지", now - 40 * day)
    private val firstPage = NoticePage(listOf(recent, old), nextCursor = "c2")
    private val secondPage = NoticePage(listOf(NoticeSummary("third", "셋째", now - 2 * day)), nextCursor = null)

    @Test
    fun `안 읽은 최근 공지가 있으면 점이 켜지고 상세를 읽으면 꺼진다`() =
        runTest {
            val repository = FakeNoticeRepository(pages = mapOf(null to DomainResult.Success(firstPage)))
            val readRepository = FakeNoticeReadRepository()
            val viewModel = NoticeUnreadViewModel(repository, readRepository)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasUnread)

            readRepository.markRead("recent")
            advanceUntilIdle()
            // 30일이 지난 공지는 열지 않아도 안 읽음으로 세지 않는다.
            assertFalse(viewModel.uiState.value.hasUnread)
        }

    @Test
    fun `조회 실패는 점을 끄고 다시 보일 때 다시 읽는다`() =
        runTest {
            val repository = FakeNoticeRepository()
            val viewModel = NoticeUnreadViewModel(repository, FakeNoticeReadRepository())
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasUnread)

            repository.pages = mapOf(null to DomainResult.Success(firstPage))
            viewModel.onIntent(NoticeUnreadIntent.Refresh)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasUnread)
            assertEquals(2, repository.listCalls.size)
        }

    @Test
    fun `목록은 커서로 다음 페이지를 이어 붙이고 끝에서 멈춘다`() =
        runTest {
            val repository =
                FakeNoticeRepository(
                    pages = mapOf(null to DomainResult.Success(firstPage), "c2" to DomainResult.Success(secondPage)),
                )
            val viewModel = NoticeListViewModel(repository, FakeNoticeReadRepository(listOf("old")))
            advanceUntilIdle()
            assertEquals(
                listOf("recent", "old"),
                viewModel.uiState.value.notices
                    .map { it.id },
            )
            assertEquals(listOf("old"), viewModel.uiState.value.readIds)
            assertTrue(viewModel.uiState.value.hasMore)

            viewModel.onIntent(NoticeListIntent.LoadMore)
            advanceUntilIdle()
            assertEquals(
                listOf("recent", "old", "third"),
                viewModel.uiState.value.notices
                    .map { it.id },
            )
            assertFalse(viewModel.uiState.value.hasMore)

            viewModel.onIntent(NoticeListIntent.LoadMore)
            advanceUntilIdle()
            assertEquals(listOf(null, "c2"), repository.listCalls)
        }

    @Test
    fun `다음 페이지 실패는 기존 목록을 두고 실패 자리만 켠다`() =
        runTest {
            val repository = FakeNoticeRepository(pages = mapOf(null to DomainResult.Success(firstPage)))
            val viewModel = NoticeListViewModel(repository, FakeNoticeReadRepository())
            advanceUntilIdle()

            viewModel.onIntent(NoticeListIntent.LoadMore)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals(2, state.notices.size)
            assertTrue(state.loadMoreFailed)
            assertFalse(state.isLoadingMore)
            assertFalse(state.loadFailed)
        }

    @Test
    fun `첫 조회 실패는 실패 화면이고 다시 시도하면 목록이 온다`() =
        runTest {
            val repository = FakeNoticeRepository()
            val viewModel = NoticeListViewModel(repository, FakeNoticeReadRepository())
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.loadFailed)

            repository.pages = mapOf(null to DomainResult.Success(firstPage))
            viewModel.onIntent(NoticeListIntent.Retry)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.loadFailed)
            assertEquals(2, viewModel.uiState.value.notices.size)
        }

    @Test
    fun `상세를 받으면 읽음으로 기록한다`() =
        runTest {
            val detail = NoticeDetail("recent", "새 공지", "본문", now - day, now - day)
            val repository = FakeNoticeRepository(details = mapOf("recent" to DomainResult.Success(detail)))
            val readRepository = FakeNoticeReadRepository(listOf("other"))
            val viewModel = NoticeDetailViewModel("recent", repository, readRepository)
            advanceUntilIdle()

            assertEquals(detail, viewModel.uiState.value.notice)
            assertEquals(listOf("recent", "other"), readRepository.ids.value)
        }

    @Test
    fun `404 는 없음 상태이고 읽음으로 기록하지 않으며 다른 실패는 재시도 자리다`() =
        runTest {
            val repository = FakeNoticeRepository(details = mapOf("gone" to notFound()))
            val readRepository = FakeNoticeReadRepository()
            val gone = NoticeDetailViewModel("gone", repository, readRepository)
            advanceUntilIdle()
            assertTrue(gone.uiState.value.notFound)
            assertNull(gone.uiState.value.notice)
            assertEquals(emptyList<String>(), readRepository.ids.value)

            repository.details = mapOf("x" to DomainResult.Failure(DomainError.Network))
            val failed = NoticeDetailViewModel("x", repository, readRepository)
            advanceUntilIdle()
            assertTrue(failed.uiState.value.loadFailed)
            assertFalse(failed.uiState.value.notFound)
        }
}
