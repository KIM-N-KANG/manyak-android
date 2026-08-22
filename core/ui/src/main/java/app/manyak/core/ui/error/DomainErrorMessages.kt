package app.manyak.core.ui.error

import androidx.annotation.StringRes
import app.manyak.core.domain.error.DomainError
import app.manyak.core.domain.session.SessionEndNotice
import app.manyak.core.ui.R

/**
 * 오류 타입을 문자열 리소스로 바꾸는 지점. 이 변환은 `:core:ui` 만 한다(하네스 §3-3-2).
 *
 * [DomainError.ProviderCancelled] 는 문구가 없다 — 사용자가 스스로 닫은 것이라 실패 안내를 띄우면
 * 오히려 방해가 된다. 호출부가 null 을 받으면 아무것도 보여 주지 않는다.
 */
@StringRes
fun DomainError.messageResOrNull(): Int? =
    when (this) {
        DomainError.Network -> R.string.error_network
        DomainError.Serialization -> R.string.error_server
        is DomainError.Server -> R.string.error_server
        DomainError.Unauthorized -> R.string.error_unauthorized
        DomainError.AccountSuspended -> R.string.error_account_suspended
        DomainError.ProviderCancelled -> null
        is DomainError.ProviderFailed -> R.string.error_provider_failed
        is DomainError.ProviderNotConfigured -> R.string.error_provider_not_configured
        DomainError.Unknown -> R.string.error_unknown
    }

/**
 * 세션이 끝난 이유를 사용자에게 알린다.
 *
 * 사용자가 스스로 로그아웃한 경우에는 안내하지 않는다. 정지 계정은 일반 로그아웃과 **구분되는 안내**를
 * 보여야 하며 정지 사유는 노출하지 않는다(서버 계약).
 */
@StringRes
fun SessionEndNotice.messageResOrNull(): Int? =
    when (this) {
        SessionEndNotice.USER_REQUESTED -> null
        SessionEndNotice.REAUTHENTICATION_REQUIRED -> R.string.session_ended_reauthentication_required
        SessionEndNotice.ACCOUNT_SUSPENDED -> R.string.session_ended_account_suspended
        SessionEndNotice.TOKEN_PERSISTENCE_FAILED -> R.string.session_ended_token_persistence_failed
    }
