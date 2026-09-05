package app.manyak.feature.my

import androidx.annotation.StringRes
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.feedback.FeedbackRepository
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.ui.R
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
    @StringRes val bodyErrorRes: Int? = null,
    @StringRes val emailErrorRes: Int? = null,
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

    data class ValidationFailed(
        @StringRes val bodyErrorRes: Int?,
        @StringRes val emailErrorRes: Int?,
    ) : FeedbackEvent

    data object SubmitStarted : FeedbackEvent

    data object SubmitSucceeded : FeedbackEvent

    data object SubmitFailed : FeedbackEvent
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
        private val analytics: Analytics,
    ) : MviViewModel<FeedbackIntent, FeedbackUiState, FeedbackEvent, FeedbackEffect>(FeedbackUiState()) {
        init {
            analytics.track(AnalyticsEvent.FeedbackViewed)
        }

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
                    state.copy(body = event.body, bodyErrorRes = state.bodyErrorRes.takeIf { event.body.isBlank() })

                is FeedbackEvent.EmailChanged -> state.copy(email = event.email, emailErrorRes = null)
                is FeedbackEvent.ValidationFailed ->
                    state.copy(bodyErrorRes = event.bodyErrorRes, emailErrorRes = event.emailErrorRes)

                FeedbackEvent.SubmitStarted ->
                    state.copy(isSubmitting = true, bodyErrorRes = null, emailErrorRes = null)

                FeedbackEvent.SubmitSucceeded -> FeedbackUiState()
                FeedbackEvent.SubmitFailed -> state.copy(isSubmitting = false)
            }

        private suspend fun submit() {
            val state = uiState.value
            if (state.isSubmitting) return
            val body = state.body.trim()
            val email = state.email.trim()
            val bodyError = R.string.feedback_error_empty.takeIf { body.isEmpty() }
            // 이메일은 선택 입력이라 비어 있으면 검사하지 않는다. 적었다면 보내기 전에 형식을 본다.
            val emailError = R.string.feedback_error_email.takeIf { email.isNotEmpty() && !EmailFormat.matches(email) }
            if (bodyError != null || emailError != null) {
                dispatchEvent(FeedbackEvent.ValidationFailed(bodyError, emailError))
                return
            }
            dispatchEvent(FeedbackEvent.SubmitStarted)
            analytics.track(AnalyticsEvent.FeedbackFormSubmitted)
            when (feedbackRepository.submitFeedback(body = body, email = email)) {
                is DomainResult.Success -> {
                    dispatchEvent(FeedbackEvent.SubmitSucceeded)
                    dispatchEffect(FeedbackEffect.Submitted)
                }

                is DomainResult.Failure -> {
                    // 실패 문구는 토스트가 맡는다. 입력 아래에 남기면 고칠 것이 없는 오류를 붙들게 된다.
                    dispatchEvent(FeedbackEvent.SubmitFailed)
                    dispatchEffect(FeedbackEffect.SubmitFailed)
                }
            }
        }
    }

/**
 * 이메일 형식. 서버가 거절할 값을 보내기 전에 걸러 낸다 — 형식만 고치면 되는 입력이
 * "전송에 실패했어요" 로만 돌아오면 무엇을 고쳐야 하는지 알 수 없다.
 *
 * 웹은 `input[type=email]` 의 브라우저 기본 검증이 이 자리를 맡는다. 앱에는 그 대응이 없어
 * 같은 수준(공백 없는 로컬@도메인.최상위)만 본다. 더 좁히지 않는 것은 실제로 쓰이는 주소를
 * 앱이 막는 쪽이 서버가 거절하는 쪽보다 나쁘기 때문이다.
 */
private val EmailFormat = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")
