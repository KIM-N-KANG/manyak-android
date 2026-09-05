package app.manyak.feature.my

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.feedback.FeedbackRepository
import app.manyak.core.analytics.NoOpAnalytics
import app.manyak.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class FeedbackViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 형식이 어긋난 주소를 서버까지 보내면 "전송에 실패했어요" 로만 돌아와, 고칠 곳이 이메일
     * 한 칸이라는 것을 알 수 없다.
     */
    @Test
    fun `이메일 형식이 어긋나면 보내지 않고 그 칸에 오류를 세운다`() =
        runTest {
            val repository = FakeFeedbackRepository()
            val viewModel = FeedbackViewModel(repository, NoOpAnalytics)

            viewModel.onIntent(FeedbackIntent.BodyChanged("채팅이 조금 느려요"))
            viewModel.onIntent(FeedbackIntent.EmailChanged("마냑"))
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.Submit)
            advanceUntilIdle()

            assertEquals(0, repository.submitCount)
            assertEquals(R.string.feedback_error_email, viewModel.uiState.value.emailErrorRes)
            assertNull(viewModel.uiState.value.bodyErrorRes)
        }

    /** 답변이 필요 없을 때는 비워 두는 칸이라, 비어 있는 것을 형식 오류로 보면 안 된다. */
    @Test
    fun `이메일을 비워 두면 그대로 보낸다`() =
        runTest {
            val repository = FakeFeedbackRepository()
            val viewModel = FeedbackViewModel(repository, NoOpAnalytics)

            viewModel.onIntent(FeedbackIntent.BodyChanged("채팅이 조금 느려요"))
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.Submit)
            advanceUntilIdle()

            assertEquals(1, repository.submitCount)
            assertEquals("", repository.lastEmail)
        }

    @Test
    fun `형식에 맞는 이메일은 그대로 보낸다`() =
        runTest {
            val repository = FakeFeedbackRepository()
            val viewModel = FeedbackViewModel(repository, NoOpAnalytics)

            viewModel.onIntent(FeedbackIntent.BodyChanged("채팅이 조금 느려요"))
            viewModel.onIntent(FeedbackIntent.EmailChanged("reader@manyak.app"))
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.Submit)
            advanceUntilIdle()

            assertEquals(1, repository.submitCount)
            assertEquals("reader@manyak.app", repository.lastEmail)
        }

    /** 고친 값 옆에 남은 오류는 지금 상태를 말하지 않는다. */
    @Test
    fun `이메일을 고치면 오류가 사라진다`() =
        runTest {
            val repository = FakeFeedbackRepository()
            val viewModel = FeedbackViewModel(repository, NoOpAnalytics)

            viewModel.onIntent(FeedbackIntent.BodyChanged("채팅이 조금 느려요"))
            viewModel.onIntent(FeedbackIntent.EmailChanged("마냑"))
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.Submit)
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.EmailChanged("마냑@"))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.emailErrorRes)
        }

    /** 본문이 비었는지와 이메일 형식은 각자 자기 칸에서 말한다. */
    @Test
    fun `본문이 비고 이메일도 어긋나면 두 오류를 함께 세운다`() =
        runTest {
            val repository = FakeFeedbackRepository()
            val viewModel = FeedbackViewModel(repository, NoOpAnalytics)

            viewModel.onIntent(FeedbackIntent.EmailChanged("마냑"))
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.Submit)
            advanceUntilIdle()

            assertEquals(0, repository.submitCount)
            assertEquals(R.string.feedback_error_empty, viewModel.uiState.value.bodyErrorRes)
            assertEquals(R.string.feedback_error_email, viewModel.uiState.value.emailErrorRes)
        }

    /** 전송에 실패해도 쓴 글은 남긴다 — 사라지면 다시 쓸 방법이 없다. */
    @Test
    fun `전송에 실패하면 입력을 그대로 둔다`() =
        runTest {
            val repository = FakeFeedbackRepository(result = DomainResult.Failure(DomainError.Network))
            val viewModel = FeedbackViewModel(repository, NoOpAnalytics)

            viewModel.onIntent(FeedbackIntent.BodyChanged("채팅이 조금 느려요"))
            advanceUntilIdle()
            viewModel.onIntent(FeedbackIntent.Submit)
            advanceUntilIdle()

            assertEquals("채팅이 조금 느려요", viewModel.uiState.value.body)
            assertTrue(!viewModel.uiState.value.isSubmitting)
        }
}

private class FakeFeedbackRepository(
    private val result: DomainResult<Unit> = DomainResult.Success(Unit),
) : FeedbackRepository {
    var submitCount = 0
        private set

    var lastEmail: String? = null
        private set

    override suspend fun submitFeedback(
        body: String,
        email: String?,
    ): DomainResult<Unit> {
        submitCount++
        lastEmail = email
        return result
    }
}
