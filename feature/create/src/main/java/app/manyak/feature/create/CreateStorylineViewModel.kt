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

@HiltViewModel
class CreateStorylineViewModel
    @Inject
    constructor() :
    MviViewModel<CreateStorylineIntent, CreateStorylineUiState, CreateStorylineEvent, Nothing>(
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

                // 추가 정보 단계 전환은 해당 화면 구현과 함께 붙는다.
                CreateStorylineIntent.ConfirmSelection -> Unit
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
                    "눈을 떠보니 낯선 궁전의 침대 위. 거울 속에는 어제 읽다 잠든 소설 속 " +
                        "**비운의 황녀**가 서 있다. 원작대로라면 3년 뒤 폐위되어 유배지에서 생을 마감할 운명. " +
                        "*결말을 아는 사람은 나뿐이다.* 오늘부터 조용히 판을 뒤집기로 한다. 첫걸음은 " +
                        "원작에서 나를 몰아냈던 재상의 약점을 먼저 손에 넣는 것이다.",
                    "평범한 회사원이던 내가 눈을 뜬 곳은 검과 마법의 대륙. 손에 쥔 것은 " +
                        "낡은 검 한 자루와 정체 모를 문장이 새겨진 목걸이뿐이다. 마을 사람들은 그 문장을 " +
                        "보자마자 무릎을 꿇었고, 나는 **사라진 왕가의 마지막 후계자**로 오해받기 시작한다. " +
                        "*진실을 밝히기엔 이미 너무 멀리 왔다.* 이왕 이렇게 된 것, 왕좌까지 가보기로 한다.",
                    "출근길 버스에서 눈을 감았다 떴을 뿐인데, 세상이 멈춰 있었다. 움직이는 " +
                        "것은 나와 길 건너의 낯선 남자뿐. 그는 내게 다가와 말한다 — \"드디어 찾았다, " +
                        "**두 번째 시계공**.\" 멈춘 세계의 시간을 되돌릴 수 있는 사람은 세상에 단 둘. " +
                        "*그와 손을 잡아야만 시간이 다시 흐른다.* 하지만 그는 세상이 멈춘 이유를 숨기고 있다.",
                )
        }
    }
