package app.manyak.feature.my

import androidx.annotation.StringRes
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.analytics.InviteCodeSource
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.invite.Invite
import app.manyak.core.domain.invite.InviteRepository
import app.manyak.core.domain.user.UserProfileRepository
import app.manyak.core.ui.R
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface InviteIntent {
    data object Load : InviteIntent

    data object Retry : InviteIntent

    data class CodeChanged(
        val code: String,
    ) : InviteIntent

    data object Redeem : InviteIntent
}

data class InviteUiState(
    val isLoading: Boolean = true,
    val invite: Invite? = null,
    /** 공유에 싣는 웹 주소. 빌드 값이라 화면이 아니라 여기서 들고 있는다. */
    val shareUrl: String = "",
    val code: String = "",
    val isSubmitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val inviteCode: String? get() = invite?.code

    /** 코드를 못 받았으면 복사도 공유도 할 것이 없다. */
    val isCodeUnavailable: Boolean get() = !isLoading && inviteCode == null
}

sealed interface InviteEvent {
    data object LoadStarted : InviteEvent

    data class Loaded(
        val invite: Invite,
    ) : InviteEvent

    data object LoadFailed : InviteEvent

    data class CodeChanged(
        val code: String,
    ) : InviteEvent

    data object SubmitStarted : InviteEvent

    data object SubmitSucceeded : InviteEvent

    data class SubmitFailed(
        @StringRes val errorRes: Int,
    ) : InviteEvent
}

sealed interface InviteEffect {
    data object Redeemed : InviteEffect
}

/**
 * 친구 초대.
 *
 * 내 코드 조회와 받은 코드 등록이 한 화면에 있지만 서로 영향을 주지 않는다 — 등록은 상대의 코드를
 * 쓰는 일이라 내 코드도 이번 달 보상 횟수도 그대로다. 바뀌는 것은 잔액뿐이라 프로필만 다시 읽는다.
 */
@HiltViewModel
class InviteViewModel
    @Inject
    constructor(
        private val inviteRepository: InviteRepository,
        private val userProfileRepository: UserProfileRepository,
        shareLinkProvider: InviteShareLinkProvider,
        private val analytics: Analytics,
    ) : MviViewModel<InviteIntent, InviteUiState, InviteEvent, InviteEffect>(
            InviteUiState(shareUrl = shareLinkProvider.shareUrl()),
        ) {
        init {
            analytics.track(AnalyticsEvent.InviteViewed)
        }

        override suspend fun handleIntent(intent: InviteIntent) {
            when (intent) {
                InviteIntent.Load -> if (uiState.value.invite == null) load()
                InviteIntent.Retry -> load()
                is InviteIntent.CodeChanged -> dispatchEvent(InviteEvent.CodeChanged(sanitizeInviteCode(intent.code)))
                InviteIntent.Redeem -> redeem()
            }
        }

        override fun reduce(
            state: InviteUiState,
            event: InviteEvent,
        ): InviteUiState =
            when (event) {
                InviteEvent.LoadStarted -> state.copy(isLoading = true)
                is InviteEvent.Loaded -> state.copy(isLoading = false, invite = event.invite)
                // 실패도 조회가 끝난 상태다. 코드 없음으로 그려 다시 시도할 길을 준다.
                InviteEvent.LoadFailed -> state.copy(isLoading = false, invite = Invite(null, null, null))
                // 입력을 고치는 순간 이전 오류를 지운다. 고친 값 옆에 남은 문구는 지금 상태를 말하지 않는다.
                is InviteEvent.CodeChanged -> state.copy(code = event.code, errorRes = null)
                InviteEvent.SubmitStarted -> state.copy(isSubmitting = true, errorRes = null)
                InviteEvent.SubmitSucceeded -> state.copy(isSubmitting = false, code = "", errorRes = null)
                is InviteEvent.SubmitFailed -> state.copy(isSubmitting = false, errorRes = event.errorRes)
            }

        private suspend fun load() {
            dispatchEvent(InviteEvent.LoadStarted)
            when (val result = inviteRepository.getMyInvite()) {
                is DomainResult.Success -> dispatchEvent(InviteEvent.Loaded(result.value))
                is DomainResult.Failure -> dispatchEvent(InviteEvent.LoadFailed)
            }
        }

        private suspend fun redeem() {
            val state = uiState.value
            if (state.isSubmitting) return
            val code = state.code.trim()
            if (code.isEmpty()) {
                dispatchEvent(InviteEvent.SubmitFailed(R.string.invite_code_error_empty))
                return
            }
            dispatchEvent(InviteEvent.SubmitStarted)
            analytics.track(AnalyticsEvent.InviteCodeSubmitted(InviteCodeSource.INVITE_PAGE))
            when (val result = inviteRepository.redeemInviteCode(code)) {
                is DomainResult.Success -> {
                    analytics.track(AnalyticsEvent.InviteCodeSucceeded(InviteCodeSource.INVITE_PAGE))
                    dispatchEvent(InviteEvent.SubmitSucceeded)
                    dispatchEffect(InviteEffect.Redeemed)
                    // 잔액 정본은 프로필이라 지급액을 더하지 않고 다시 읽는다.
                    userProfileRepository.refresh()
                }

                is DomainResult.Failure -> {
                    analytics.track(
                        AnalyticsEvent.InviteCodeFailed(
                            InviteCodeSource.INVITE_PAGE,
                            result.error.inviteCodeErrorType(),
                        ),
                    )
                    dispatchEvent(InviteEvent.SubmitFailed(result.error.inviteCodeMessageRes()))
                }
            }
        }
    }
