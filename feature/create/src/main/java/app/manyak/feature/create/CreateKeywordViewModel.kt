package app.manyak.feature.create

import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 키워드 선택 단계의 카테고리. 순서가 곧 탭 순서이고, 앞선 필수 카테고리가 완료되어야
 * 다음 카테고리가 잠금 해제된다.
 */
enum class KeywordCategory(
    val required: Boolean,
) {
    GENRE(required = true),
    PROTAGONIST(required = true),
    SUPPORTING_CHARACTER(required = false),
    ;

    val previous: KeywordCategory? get() = entries.getOrNull(ordinal - 1)
    val next: KeywordCategory? get() = entries.getOrNull(ordinal + 1)
}

data class CreateKeywordUiState(
    val activeCategory: KeywordCategory = KeywordCategory.GENRE,
    /** "다음"을 눌러 검증에 실패한 카테고리. 그 카테고리가 활성일 때만 푸터에 오류를 표시한다. */
    val validationErrorCategory: KeywordCategory? = null,
    // 키워드 선택 UI 가 아직 없어 항상 false 다. 태그 입력이 붙으면 선택 상태가 이 자리를 채운다.
    val hasGenreKeyword: Boolean = false,
    val hasProtagonistFeature: Boolean = false,
    val supportingCharacterCount: Int = SUPPORTING_CHARACTER_INITIAL_COUNT,
) {
    /**
     * 필수 카테고리만 완료 조건이 있다. 주변 인물은 선택 항목이라 항상 통과한다.
     */
    fun isComplete(category: KeywordCategory): Boolean =
        when (category) {
            KeywordCategory.GENRE -> hasGenreKeyword
            KeywordCategory.PROTAGONIST -> hasProtagonistFeature
            KeywordCategory.SUPPORTING_CHARACTER -> true
        }

    fun isUnlocked(category: KeywordCategory): Boolean =
        KeywordCategory.entries
            .take(category.ordinal)
            .all { isComplete(it) }

    companion object {
        /** 퍼널 진입 시 빈 주변 인물 카드 1장이 놓여 있다. */
        const val SUPPORTING_CHARACTER_INITIAL_COUNT: Int = 1
    }
}

sealed interface CreateKeywordIntent {
    data class SelectCategory(
        val category: KeywordCategory,
    ) : CreateKeywordIntent

    data object GoPrevious : CreateKeywordIntent

    data object GoNext : CreateKeywordIntent

    data object GenerateStorylines : CreateKeywordIntent
}

sealed interface CreateKeywordEvent {
    data class CategoryChanged(
        val category: KeywordCategory,
    ) : CreateKeywordEvent

    data class ValidationFailed(
        val category: KeywordCategory,
    ) : CreateKeywordEvent
}

/**
 * 키워드 선택 단계의 카테고리 이동과 검증.
 *
 * 필수 미충족 상태에서도 "다음" 은 활성이고, 누르면 이동하지 않고 오류를 표시한다. 오류는 그 카테고리에서
 * 키워드를 선택할 때 지워지는 계약이라, 선택 입력이 붙기 전까지는 지워지는 경로가 없다.
 */
@HiltViewModel
class CreateKeywordViewModel
    @Inject
    constructor() :
    MviViewModel<CreateKeywordIntent, CreateKeywordUiState, CreateKeywordEvent, Nothing>(CreateKeywordUiState()) {
        override suspend fun handleIntent(intent: CreateKeywordIntent) {
            val state = uiState.value
            when (intent) {
                is CreateKeywordIntent.SelectCategory ->
                    if (state.isUnlocked(intent.category)) {
                        dispatchEvent(CreateKeywordEvent.CategoryChanged(intent.category))
                    }

                CreateKeywordIntent.GoPrevious ->
                    state.activeCategory.previous?.let { previous ->
                        dispatchEvent(CreateKeywordEvent.CategoryChanged(previous))
                    }

                CreateKeywordIntent.GoNext ->
                    if (state.isComplete(state.activeCategory)) {
                        state.activeCategory.next?.let { next ->
                            dispatchEvent(CreateKeywordEvent.CategoryChanged(next))
                        }
                    } else {
                        dispatchEvent(CreateKeywordEvent.ValidationFailed(state.activeCategory))
                    }

                // 스토리라인 생성은 다음 단계 화면·API 연동과 함께 붙는다.
                CreateKeywordIntent.GenerateStorylines -> Unit
            }
        }

        override fun reduce(
            state: CreateKeywordUiState,
            event: CreateKeywordEvent,
        ): CreateKeywordUiState =
            when (event) {
                is CreateKeywordEvent.CategoryChanged -> state.copy(activeCategory = event.category)
                is CreateKeywordEvent.ValidationFailed -> state.copy(validationErrorCategory = event.category)
            }
    }
