package app.manyak.feature.create

import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 스토리라인 평가. 보조 신호이며 선택 진행을 막지 않는다. */
enum class StorylineRating {
    GOOD,
    BAD,
}

data class CreateStorylineUiState(
    val storylines: List<String> = emptyList(),
    val activeIndex: Int = 0,
    /** 스토리라인 순번별 평가. 같은 평가를 다시 누르면 해제된다. */
    val ratings: Map<Int, StorylineRating> = emptyMap(),
) {
    val activeStoryline: String? get() = storylines.getOrNull(activeIndex)

    val activeRating: StorylineRating? get() = ratings[activeIndex]
}

sealed interface CreateStorylineIntent {
    data class SelectStoryline(
        val index: Int,
    ) : CreateStorylineIntent

    data class ToggleRating(
        val rating: StorylineRating,
    ) : CreateStorylineIntent

    data object Regenerate : CreateStorylineIntent

    data object ConfirmSelection : CreateStorylineIntent
}

sealed interface CreateStorylineEvent {
    data class ActiveStorylineChanged(
        val index: Int,
    ) : CreateStorylineEvent

    data class RatingToggled(
        val index: Int,
        val rating: StorylineRating,
    ) : CreateStorylineEvent
}

sealed interface CreateStorylineEffect {
    /** 활성 스토리라인 "선택하기" — 추가 정보 단계로 넘어간다. */
    data class NavigateToAdditionalInfo(
        val storylineIndex: Int,
    ) : CreateStorylineEffect
}

@HiltViewModel
class CreateStorylineViewModel
    @Inject
    constructor() :
    MviViewModel<CreateStorylineIntent, CreateStorylineUiState, CreateStorylineEvent, CreateStorylineEffect>(
        CreateStorylineUiState(storylines = PLACEHOLDER_STORYLINES),
    ) {
        override suspend fun handleIntent(intent: CreateStorylineIntent) {
            val state = uiState.value
            when (intent) {
                is CreateStorylineIntent.SelectStoryline ->
                    if (intent.index in state.storylines.indices) {
                        dispatchEvent(CreateStorylineEvent.ActiveStorylineChanged(intent.index))
                    }

                is CreateStorylineIntent.ToggleRating ->
                    if (state.activeStoryline != null) {
                        dispatchEvent(CreateStorylineEvent.RatingToggled(state.activeIndex, intent.rating))
                    }

                // 재생성 요청과 평가 동기화는 생성 API 연동과 함께 붙는다.
                CreateStorylineIntent.Regenerate -> Unit

                CreateStorylineIntent.ConfirmSelection ->
                    if (state.activeStoryline != null) {
                        dispatchEffect(CreateStorylineEffect.NavigateToAdditionalInfo(state.activeIndex))
                    }
            }
        }

        override fun reduce(
            state: CreateStorylineUiState,
            event: CreateStorylineEvent,
        ): CreateStorylineUiState =
            when (event) {
                is CreateStorylineEvent.ActiveStorylineChanged -> state.copy(activeIndex = event.index)
                is CreateStorylineEvent.RatingToggled ->
                    state.copy(
                        ratings =
                            if (state.ratings[event.index] == event.rating) {
                                state.ratings - event.index
                            } else {
                                state.ratings + (event.index to event.rating)
                            },
                    )
            }

        companion object {
            /** 생성 API 연동 전까지 화면 확인용 임시 스토리라인. 연동 시 생성 결과로 대체한다. */
            val PLACEHOLDER_STORYLINES: List<String> =
                listOf(
                    """
                    첫 번째 게이트가 열린 날, 동우는 주변의 모든 것을 잃었다.

                    그 후, 그는 혼자서 살아남는 법을 배웠고, 그 과정에서 천재적인 능력을 각성했다.

                    도윤은 폐허에서 다시 만난 후, 냉담한 말투로 동우를 밀어냈지만 끝내 함께하기로 했다.

                    서연은 다른 세력에 속해 있었지만, 동우의 힘을 알고 협력을 제안했다.

                    그녀는 동우의 재능을 인정하지 않으면서도, 살아남기 위해 손을 잡았다.

                    셋은 버려진 연구소에서 괴물의 근원을 발견하고, 그것을 파괴하기로 결심한다.

                    동우는 이제 자신의 힘이 세상을 바꿀 수 있음을 알고, 마지막 선택을 준비한다.
                    """.trimIndent(),
                    """
                    게이트가 열리던 순간, 동우는 아무 힘도 없는 평범한 사람이었다.

                    괴물에게 쫓기던 그를 구한 것은 이미 각성자였던 도윤이었고, 그날 이후 두 사람은 서로를 떠나지 못했다.

                    동우는 도윤의 곁에서 천천히 자신의 능력을 깨달아 갔지만, *그 힘이 괴물과 같은 근원에서 나온다는 사실*은 숨겼다.

                    서연은 각성자를 관리하는 조직의 감시자로 동우에게 접근했다.

                    그녀는 동우의 정체를 의심하면서도, 그가 보여 주는 선의 앞에서 번번이 보고서를 덮었다.

                    조직이 동우를 **제거 대상**으로 분류한 날, 도윤과 서연은 각자의 이유로 그의 앞을 막아섰다.

                    동우는 자신을 믿어 준 두 사람을 위해, 힘의 근원을 마주하러 게이트 안으로 걸어 들어간다.
                    """.trimIndent(),
                    """
                    세 번째 게이트가 닫힌 뒤, 세상은 동우를 **영웅**이라 불렀다.

                    하지만 동우만은 알고 있었다. 그날 게이트를 닫은 것은 자신이 아니라, 이름조차 남기지 못한 도윤이었다는 것을.

                    *도윤이 사라진 자리에서, 동우는 그의 힘을 물려받았다.*

                    서연은 진실을 아는 유일한 목격자로, 동우의 거짓 영웅 행세를 경멸하면서도 침묵했다.

                    새로운 게이트가 열리고, 그 너머에서 도윤의 목소리가 들려오기 시작한다.

                    동우는 영웅의 자리를 버리고 도윤을 찾으러 갈 것인지, 세상이 원하는 거짓을 계속 살아갈 것인지 갈림길에 선다.

                    서연은 처음으로 동우에게 손을 내밀며 말한다. "같이 가. 진실은 둘이 들어야 덜 무거워."
                    """.trimIndent(),
                )
        }
    }
