package app.manyak.feature.my

import androidx.annotation.StringRes
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.session.SessionRepository
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 탈퇴 전 확인 항목. 하나라도 남으면 탈퇴할 수 없다. */
internal data class WithdrawalConfirmation(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
)

internal val WithdrawalConfirmations =
    listOf(
        WithdrawalConfirmation(
            titleRes = R.string.withdrawal_confirm_credit,
            descriptionRes = R.string.withdrawal_confirm_credit_description,
        ),
        WithdrawalConfirmation(
            titleRes = R.string.withdrawal_confirm_library,
            descriptionRes = R.string.withdrawal_confirm_library_description,
        ),
        WithdrawalConfirmation(
            titleRes = R.string.withdrawal_confirm_published,
            descriptionRes = R.string.withdrawal_confirm_published_description,
        ),
        WithdrawalConfirmation(
            titleRes = R.string.withdrawal_confirm_social_login,
            descriptionRes = R.string.withdrawal_confirm_social_login_description,
        ),
    )

sealed interface WithdrawalIntent {
    data class ToggleConfirmation(
        val index: Int,
    ) : WithdrawalIntent

    data object Withdraw : WithdrawalIntent
}

data class WithdrawalUiState(
    val checkedIndices: Set<Int> = emptySet(),
    val isWithdrawing: Boolean = false,
) {
    val canWithdraw: Boolean get() = checkedIndices.size == WithdrawalConfirmations.size
}

sealed interface WithdrawalEvent {
    data class ConfirmationToggled(
        val index: Int,
    ) : WithdrawalEvent

    data object WithdrawStarted : WithdrawalEvent

    data object WithdrawFailed : WithdrawalEvent
}

sealed interface WithdrawalEffect {
    data object Failed : WithdrawalEffect
}

/**
 * 회원 탈퇴.
 *
 * 성공하면 세션이 끝나 루트가 인증 그래프로 옮겨 가므로 이 화면은 완료를 알리지 않는다 —
 * 화면 자체가 사라지는 것이 결과다.
 */
@HiltViewModel
class WithdrawalViewModel
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val analytics: Analytics,
    ) : MviViewModel<WithdrawalIntent, WithdrawalUiState, WithdrawalEvent, WithdrawalEffect>(WithdrawalUiState()) {
        init {
            analytics.track(AnalyticsEvent.WithdrawalViewed)
        }

        override suspend fun handleIntent(intent: WithdrawalIntent) {
            when (intent) {
                is WithdrawalIntent.ToggleConfirmation ->
                    if (!uiState.value.isWithdrawing) {
                        dispatchEvent(WithdrawalEvent.ConfirmationToggled(intent.index))
                    }

                WithdrawalIntent.Withdraw -> withdraw()
            }
        }

        override fun reduce(
            state: WithdrawalUiState,
            event: WithdrawalEvent,
        ): WithdrawalUiState =
            when (event) {
                is WithdrawalEvent.ConfirmationToggled ->
                    state.copy(
                        checkedIndices =
                            if (event.index in state.checkedIndices) {
                                state.checkedIndices - event.index
                            } else {
                                state.checkedIndices + event.index
                            },
                    )

                WithdrawalEvent.WithdrawStarted -> state.copy(isWithdrawing = true)
                WithdrawalEvent.WithdrawFailed -> state.copy(isWithdrawing = false)
            }

        private suspend fun withdraw() {
            val state = uiState.value
            if (state.isWithdrawing || !state.canWithdraw) return
            dispatchEvent(WithdrawalEvent.WithdrawStarted)
            // 성공하면 진행 표시를 내리지 않는다 — 세션 종료가 이 화면을 걷어낼 때까지 잠긴 채로 둔다.
            when (sessionRepository.withdraw()) {
                // 종료 정리가 프로필을 비워 식별자를 떼기 전에 보낸다. 늦으면 익명 탈퇴로 남는다.
                is DomainResult.Success -> analytics.track(AnalyticsEvent.WithdrawalCompleted)

                is DomainResult.Failure -> {
                    dispatchEvent(WithdrawalEvent.WithdrawFailed)
                    dispatchEffect(WithdrawalEffect.Failed)
                }
            }
        }
    }
