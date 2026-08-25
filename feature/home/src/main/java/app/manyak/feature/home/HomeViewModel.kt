package app.manyak.feature.home

import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.story.CreationResumePoint
import app.manyak.core.domain.story.PendingStoryCreation
import app.manyak.core.domain.story.PendingStoryCreationStore
import app.manyak.core.domain.story.resumePoint
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 이어서 만들기 배너의 표시 정보. 레코드 존재만 확인하며 서버 조회는 하지 않는다(3-1). */
data class PendingCreationBanner(
    /** 완성 진행 중이면 "완성 중인 스토리가 있어요", 그 외에는 "만들고 있는 스토리가 있어요". */
    val isCompleting: Boolean,
    val resumePoint: CreationResumePoint,
)

data class HomeUiState(
    val pendingBanner: PendingCreationBanner? = null,
    /** FAB 등 배너가 아닌 경로로 진입하려는데 임시 저장본이 있어 이어서/새로 만들기를 묻는 중. */
    val showResumeChoiceDialog: Boolean = false,
)

sealed interface HomeIntent {
    /** 제작 퍼널 진입 시도(FAB). 진행 레코드가 있으면 다이얼로그로 묻는다. */
    data object CreateStory : HomeIntent

    /** 배너의 "이어서 만들기". */
    data object ResumeCreation : HomeIntent

    /** 다이얼로그의 "새로 만들기" — 레코드를 폐기하고 키워드 단계부터 시작한다. */
    data object StartNewCreation : HomeIntent

    data object DismissResumeChoiceDialog : HomeIntent
}

sealed interface HomeEvent {
    data class PendingCreationChanged(
        val banner: PendingCreationBanner?,
    ) : HomeEvent

    data class ResumeChoiceDialogVisibleChanged(
        val visible: Boolean,
    ) : HomeEvent
}

sealed interface HomeEffect {
    /** 새 생성으로 퍼널 진입 — 키워드 단계부터. */
    data object NavigateToCreate : HomeEffect

    /** 재개 진입 — 레코드 단계까지 퍼널 백스택을 쌓는다. */
    data class NavigateToResume(
        val resumePoint: CreationResumePoint,
    ) : HomeEffect
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val pendingCreationStore: PendingStoryCreationStore,
    ) : MviViewModel<HomeIntent, HomeUiState, HomeEvent, HomeEffect>(HomeUiState()) {
        init {
            viewModelScope.launch {
                pendingCreationStore.record.collect { record ->
                    dispatchEvent(HomeEvent.PendingCreationChanged(record?.toBanner()))
                }
            }
        }

        override suspend fun handleIntent(intent: HomeIntent) {
            val state = uiState.value
            when (intent) {
                HomeIntent.CreateStory ->
                    if (state.pendingBanner == null) {
                        dispatchEffect(HomeEffect.NavigateToCreate)
                    } else {
                        dispatchEvent(HomeEvent.ResumeChoiceDialogVisibleChanged(visible = true))
                    }

                HomeIntent.ResumeCreation ->
                    state.pendingBanner?.let { banner ->
                        dispatchEvent(HomeEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                        dispatchEffect(HomeEffect.NavigateToResume(banner.resumePoint))
                    }

                HomeIntent.StartNewCreation -> {
                    // 레코드 폐기가 진입보다 먼저다 — 레코드가 남은 채 들어가면 재개로 복원된다.
                    pendingCreationStore.clear()
                    dispatchEvent(HomeEvent.ResumeChoiceDialogVisibleChanged(visible = false))
                    dispatchEffect(HomeEffect.NavigateToCreate)
                }

                HomeIntent.DismissResumeChoiceDialog ->
                    dispatchEvent(HomeEvent.ResumeChoiceDialogVisibleChanged(visible = false))
            }
        }

        override fun reduce(
            state: HomeUiState,
            event: HomeEvent,
        ): HomeUiState =
            when (event) {
                is HomeEvent.PendingCreationChanged ->
                    state.copy(
                        pendingBanner = event.banner,
                        // 다이얼로그가 열린 사이 레코드가 사라졌으면 물을 것도 없다.
                        showResumeChoiceDialog = state.showResumeChoiceDialog && event.banner != null,
                    )

                is HomeEvent.ResumeChoiceDialogVisibleChanged ->
                    state.copy(showResumeChoiceDialog = event.visible)
            }
    }

private fun PendingStoryCreation.toBanner(): PendingCreationBanner =
    PendingCreationBanner(
        isCompleting = this is PendingStoryCreation.CompletingStory,
        resumePoint = resumePoint(),
    )
