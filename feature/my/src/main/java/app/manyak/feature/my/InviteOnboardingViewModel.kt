package app.manyak.feature.my

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.error.DomainResult
import app.manyak.core.domain.invite.InviteOnboardingRepository
import app.manyak.core.domain.invite.InviteRepository
import app.manyak.core.domain.user.UserProfileRepository
import app.manyak.core.ui.R
import app.manyak.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InviteOnboardingIntent {
    data class CodeChanged(
        val code: String,
    ) : InviteOnboardingIntent

    data object Submit : InviteOnboardingIntent

    data object Skip : InviteOnboardingIntent
}

data class InviteOnboardingUiState(
    /** 저장소가 세운 안내 차례 표시. */
    val pending: Boolean = false,
    /** 이번 실행에서 등록·건너뛰기로 닫았다. 저장 실패로 [pending] 이 남아도 다시 열지 않는다. */
    val dismissed: Boolean = false,
    val code: String = "",
    val isSubmitting: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val isVisible: Boolean get() = pending && !dismissed
}

sealed interface InviteOnboardingEvent {
    data class PendingChanged(
        val pending: Boolean,
    ) : InviteOnboardingEvent

    data class CodeChanged(
        val code: String,
    ) : InviteOnboardingEvent

    data object SubmitStarted : InviteOnboardingEvent

    data class SubmitFailed(
        @StringRes val errorRes: Int,
    ) : InviteOnboardingEvent

    data object Dismissed : InviteOnboardingEvent
}

sealed interface InviteOnboardingEffect {
    data object Redeemed : InviteOnboardingEffect
}

/**
 * 신규 가입 직후 초대 코드 안내.
 *
 * 표시의 정본은 저장소이고 화면은 그것을 구독한다. 닫기는 저장소 지우기와 화면 상태 둘 다로 처리하는데,
 * 지우기가 실패해도 다이얼로그가 그 자리에서 다시 뜨지 않게 하기 위해서다 — 실패의 결과는 다음
 * 실행에서 한 번 더 뜨는 것뿐이다.
 */
@HiltViewModel
class InviteOnboardingViewModel
    @Inject
    constructor(
        private val inviteRepository: InviteRepository,
        private val onboardingRepository: InviteOnboardingRepository,
        private val userProfileRepository: UserProfileRepository,
    ) : MviViewModel<InviteOnboardingIntent, InviteOnboardingUiState, InviteOnboardingEvent, InviteOnboardingEffect>(
            InviteOnboardingUiState(),
        ) {
        init {
            viewModelScope.launch {
                onboardingRepository.pending.collect { pending ->
                    dispatchEvent(InviteOnboardingEvent.PendingChanged(pending))
                }
            }
        }

        override suspend fun handleIntent(intent: InviteOnboardingIntent) {
            when (intent) {
                is InviteOnboardingIntent.CodeChanged ->
                    dispatchEvent(InviteOnboardingEvent.CodeChanged(sanitizeInviteCode(intent.code)))

                InviteOnboardingIntent.Submit -> submit()
                InviteOnboardingIntent.Skip -> dismiss()
            }
        }

        override fun reduce(
            state: InviteOnboardingUiState,
            event: InviteOnboardingEvent,
        ): InviteOnboardingUiState =
            when (event) {
                is InviteOnboardingEvent.PendingChanged -> state.copy(pending = event.pending)
                // 입력을 고치는 순간 이전 오류를 지운다. 고친 값 옆에 남은 문구는 지금 상태를 말하지 않는다.
                is InviteOnboardingEvent.CodeChanged -> state.copy(code = event.code, errorRes = null)
                InviteOnboardingEvent.SubmitStarted -> state.copy(isSubmitting = true, errorRes = null)
                is InviteOnboardingEvent.SubmitFailed -> state.copy(isSubmitting = false, errorRes = event.errorRes)
                InviteOnboardingEvent.Dismissed -> state.copy(dismissed = true, isSubmitting = false)
            }

        private suspend fun submit() {
            val state = uiState.value
            if (state.isSubmitting) return
            val code = state.code.trim()
            if (code.isEmpty()) {
                dispatchEvent(InviteOnboardingEvent.SubmitFailed(R.string.invite_code_error_empty))
                return
            }
            dispatchEvent(InviteOnboardingEvent.SubmitStarted)
            when (val result = inviteRepository.redeemInviteCode(code)) {
                is DomainResult.Success -> {
                    dispatchEffect(InviteOnboardingEffect.Redeemed)
                    // 잔액 정본은 프로필이라 지급액을 더하지 않고 다시 읽는다.
                    userProfileRepository.refresh()
                    dismiss()
                }

                is DomainResult.Failure ->
                    dispatchEvent(InviteOnboardingEvent.SubmitFailed(result.error.inviteCodeMessageRes()))
            }
        }

        private suspend fun dismiss() {
            dispatchEvent(InviteOnboardingEvent.Dismissed)
            onboardingRepository.acknowledge()
        }
    }

/**
 * 입력 단계 정리 — 영문·숫자만 남기고 대문자로 맞춘 뒤 코드 길이로 자른다.
 *
 * 한글 IME 로도 입력창에 글자가 들어오므로 코드에 없는 문자를 여기서 떨군다. 자판을 영문으로
 * 유도해도 마지막 IME 에 따라 한글이 먼저 뜨는 기기가 있다.
 */
internal fun sanitizeInviteCode(value: String): String =
    value
        .filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
        .uppercase()
        .take(INVITE_CODE_MAX_LENGTH)

/** 등록 실패 사유별 문구. 안내 시트와 친구 초대 화면이 같은 계약을 읽는다. */
@StringRes
internal fun DomainError.inviteCodeMessageRes(): Int {
    if (this !is DomainError.Server) return R.string.invite_code_error_failed
    return when {
        status == HTTP_BAD_REQUEST || status == HTTP_NOT_FOUND -> R.string.invite_code_error_not_found
        status == HTTP_CONFLICT && code == CODE_SELF -> R.string.invite_code_error_self
        status == HTTP_CONFLICT && code == CODE_ALREADY_REDEEMED -> R.string.invite_code_error_already_redeemed
        else -> R.string.invite_code_error_failed
    }
}

/** 영문·숫자 8자. */
internal const val INVITE_CODE_MAX_LENGTH = 8

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val CODE_SELF = "INVITE_SELF_CODE"
private const val CODE_ALREADY_REDEEMED = "INVITE_ALREADY_REDEEMED"
