package app.manyak.common.presentation.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * `MviViewModel` 의 큐 계약 테스트.
 *
 * 이 베이스 클래스를 둔 이유가 화면별 편차를 막는 것이므로, 큐 계약이 깨지면 여기서 드러나야 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `동시에 들어온 사건도 한 소비자에서 순서대로 줄어든다`() =
        runTest(dispatcher) {
            val viewModel = RecordingViewModel()

            repeat(10) { index -> viewModel.onIntent(Intent.Record(index)) }
            advanceUntilIdle()

            assertEquals((0 until 10).toList(), viewModel.uiState.value.recorded)
        }

    @Test
    fun `채널 용량을 넘겨도 사건을 버리지 않는다`() =
        runTest(dispatcher) {
            val viewModel = RecordingViewModel()
            val overCapacity = MviViewModel.CHANNEL_CAPACITY * 2

            repeat(overCapacity) { index -> viewModel.onIntent(Intent.Record(index)) }
            advanceUntilIdle()

            assertEquals(overCapacity, viewModel.uiState.value.recorded.size)
        }

    @Test
    fun `이미 소비한 효과는 재수집에 다시 나오지 않는다`() =
        runTest(dispatcher) {
            val viewModel = RecordingViewModel()

            viewModel.onIntent(Intent.Notify("first"))
            val firstCollector = launch { assertEquals("first", viewModel.uiEffect.first()) }
            advanceUntilIdle()
            firstCollector.join()

            // 화면이 다시 붙어 수집을 시작해도 이미 소비한 효과는 replay 되지 않는다.
            val replayed = withTimeoutOrNull(REPLAY_PROBE_MILLIS) { viewModel.uiEffect.first() }

            assertNull(replayed)
        }

    @Test
    fun `화면이 일시 정지된 동안의 효과는 남아 있다가 재수집에서 전달된다`() =
        runTest(dispatcher) {
            val viewModel = RecordingViewModel()

            viewModel.onIntent(Intent.Notify("kept"))
            advanceUntilIdle()
            yield()

            val received = viewModel.uiEffect.first()

            assertEquals("kept", received)
        }

    private sealed interface Intent {
        data class Record(
            val value: Int,
        ) : Intent

        data class Notify(
            val message: String,
        ) : Intent
    }

    private data class State(
        val recorded: List<Int> = emptyList(),
    )

    private data class Recorded(
        val value: Int,
    )

    private class RecordingViewModel : MviViewModel<Intent, State, Recorded, String>(State()) {
        override suspend fun handleIntent(intent: Intent) {
            when (intent) {
                is Intent.Record -> dispatchEvent(Recorded(intent.value))
                is Intent.Notify -> dispatchEffect(intent.message)
            }
        }

        override fun reduce(
            state: State,
            event: Recorded,
        ): State = state.copy(recorded = state.recorded + event.value)
    }

    private companion object {
        const val REPLAY_PROBE_MILLIS = 100L
    }
}
