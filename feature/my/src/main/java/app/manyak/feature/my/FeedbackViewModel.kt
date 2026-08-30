package app.manyak.feature.my

import androidx.annotation.StringRes
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.feedback.FeedbackRepository
import app.manyak.core.ui.R
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface FeedbackIntent {
    data class BodyChanged(
        val body: String,
    ) : FeedbackIntent

    data class EmailChanged(
        val email: String,
    ) : FeedbackIntent

    data object Submit : FeedbackIntent
}

data class FeedbackUiState(
    val body: String = "",
    val email: String = "",
    val isSubmitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    companion object {
        const val BODY_MAX_LENGTH = 500
        const val EMAIL_MAX_LENGTH = 320
    }
}

sealed interface FeedbackEvent {
    data class BodyChanged(
        val body: String,
    ) : FeedbackEvent

    data class EmailChanged(
        val email: String,
    ) : FeedbackEvent

    data object SubmitStarted : FeedbackEvent

    data object SubmitSucceeded : FeedbackEvent

    data class SubmitFailed(
        @StringRes val errorRes: Int?,
    ) : FeedbackEvent
}

sealed interface FeedbackEffect {
    data object Submitted : FeedbackEffect

    data object SubmitFailed : FeedbackEffect
}

/**
 * 피드백 작성·전송.
 *
 * 전송에 실패하면 입력을 그대로 둔다 — 쓴 글이 사라지면 다시 쓸 방법이 없다. 비운 뒤 성공을 알리는
 * 것은 서버가 받은 뒤뿐이다.
 */
@HiltViewModel
class FeedbackViewModel
    @Inject
    constructor(
        private val feedbackRepository: FeedbackRepository,
    ) : MviViewModel<FeedbackIntent, FeedbackUiState, FeedbackEvent, FeedbackEffect>(FeedbackUiState()) {
        override suspend fun handleIntent(intent: FeedbackIntent) {
            when (intent) {
                is FeedbackIntent.BodyChanged ->
                    dispatchEvent(FeedbackEvent.BodyChanged(intent.body.take(FeedbackUiState.BODY_MAX_LENGTH)))

                is FeedbackIntent.EmailChanged ->
                    dispatchEvent(FeedbackEvent.EmailChanged(intent.email.take(FeedbackUiState.EMAIL_MAX_LENGTH)))

                FeedbackIntent.Submit -> submit()
            }
        }

        override fun reduce(
            state: FeedbackUiState,
            event: FeedbackEvent,
        ): FeedbackUiState =
            when (event) {
                // 내용을 채우는 순간 이전 오류를 지운다. 고친 값 옆에 남은 문구는 지금 상태를 말하지 않는다.
                is FeedbackEvent.BodyChanged ->
                    state.copy(body = event.body, errorRes = state.errorRes.takeIf { event.body.isBlank() })

                is FeedbackEvent.EmailChanged -> state.copy(email = event.email)
                FeedbackEvent.SubmitStarted -> state.copy(isSubmitting = true, errorRes = null)
                FeedbackEvent.SubmitSucceeded -> FeedbackUiState()
                is FeedbackEvent.SubmitFailed -> state.copy(isSubmitting = false, errorRes = event.errorRes)
            }

        private suspend fun submit() {
            val state = uiState.value
            if (state.isSubmitting) return
            val body = state.body.trim()
            if (body.isEmpty()) {
                dispatchEvent(FeedbackEvent.SubmitFailed(R.string.feedback_error_empty))
                return
            }
            dispatchEvent(FeedbackEvent.SubmitStarted)
            when (feedbackRepository.submitFeedback(body = body, email = state.email.trim())) {
                is DomainResult.Success -> {
                    dispatchEvent(FeedbackEvent.SubmitSucceeded)
                    dispatchEffect(FeedbackEffect.Submitted)
                }

                is DomainResult.Failure -> {
                    // 실패 문구는 토스트가 맡는다. 입력 아래에 남기면 고칠 것이 없는 오류를 붙들게 된다.
                    dispatchEvent(FeedbackEvent.SubmitFailed(null))
                    dispatchEffect(FeedbackEffect.SubmitFailed)
                }
            }
        }
    }
