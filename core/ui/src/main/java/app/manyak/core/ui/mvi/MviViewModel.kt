package app.manyak.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 모든 화면 ViewModel 의 베이스.
 *
 * 흐름은 `onIntent` → (필요하면 부수효과) → [dispatchEvent] → [reduce] → 새 상태다.
 * [reduce] 는 **순수 함수**이며 그 안에서 부수효과를 수행하지 않는다.
 *
 * 큐 계약이 이 클래스에 모여 있는 이유는 화면마다 다시 작성하면 편차가 생기기 때문이다.
 * - Intent·ReducerEvent·UiEffect 는 각각 용량 [CHANNEL_CAPACITY] 의 채널로 받고 overflow 는 `SUSPEND` 다.
 *  `DROP_OLDEST`·`trySend` 실패 무시는 쓰지 않는다 — 사용자의 입력이나 상태 전이를 조용히 버리게 된다.
 * - 각 채널의 소비 코루틴은 **하나**다. 그래서 동시에 완료된 부수효과가 만든 이벤트도 큐에 들어온
 *  순서대로 하나씩 reduce 된다.
 * - `UiEffect` 는 replay 하지 않는다. 화면 호스트 하나만 소비하며, 이미 소비한 효과는 재수집에 다시
 *  나오지 않는다.
 *
 * @param I 사용자·시스템 입력
 * @param S 화면이 그리는 불변 스냅숏
 * @param E 상태를 바꾸는 사건
 * @param F 한 번만 소비하는 화면 수명 출력. 없으면 [Nothing] 을 쓴다
 */
abstract class MviViewModel<I : Any, S : Any, E : Any, F : Any>(
    initialState: S,
) : ViewModel() {
    private val intents = Channel<I>(CHANNEL_CAPACITY, BufferOverflow.SUSPEND)
    private val reducerEvents = Channel<E>(CHANNEL_CAPACITY, BufferOverflow.SUSPEND)
    private val effects = Channel<F>(CHANNEL_CAPACITY, BufferOverflow.SUSPEND)

    private val state = MutableStateFlow(initialState)

    /** 재구독자는 항상 최신값을 받는다. */
    val uiState: StateFlow<S> = state.asStateFlow()

    /** 화면 호스트 **하나만** `repeatOnLifecycle(STARTED)` 에서 수집한다. */
    val uiEffect: Flow<F> = effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            for (intent in intents) handleIntent(intent)
        }
        viewModelScope.launch {
            for (event in reducerEvents) state.value = reduce(state.value, event)
        }
    }

    /** 화면은 이것만 호출한다. 상태를 직접 바꾸거나 Repository 를 부르지 않는다. */
    fun onIntent(intent: I) {
        viewModelScope.launch { intents.send(intent) }
    }

    protected suspend fun dispatchEvent(event: E) {
        reducerEvents.send(event)
    }

    protected suspend fun dispatchEffect(effect: F) {
        effects.send(effect)
    }

    /**
     * 긴 작업을 여기서 직접 기다리면 다음 입력이 막힌다. 병렬성이 필요한 작업은
     * `viewModelScope` 의 자식 작업으로 명시적으로 시작하고, 중복 실행은 상태의 진행 플래그로 막는다.
     */
    protected abstract suspend fun handleIntent(intent: I)

    protected abstract fun reduce(
        state: S,
        event: E,
    ): S

    companion object {
        const val CHANNEL_CAPACITY: Int = 64
    }
}
