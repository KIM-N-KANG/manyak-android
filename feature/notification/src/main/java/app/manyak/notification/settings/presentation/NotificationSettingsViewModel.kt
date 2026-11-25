package app.manyak.notification.settings.presentation

import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notification.consent.entity.ConsentChange
import app.manyak.notification.consent.entity.ConsentNotice
import app.manyak.notification.settings.domain.PushSettingsRepository
import app.manyak.notification.settings.entity.PushSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class PushSettingKind { SERVICE, MARKETING, MARKETING_NIGHT }

sealed interface NotificationSettingsIntent {
    data object Load : NotificationSettingsIntent

    data object Retry : NotificationSettingsIntent

    data class Toggle(
        val kind: PushSettingKind,
    ) : NotificationSettingsIntent

    data object DismissNotice : NotificationSettingsIntent
}

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val settings: PushSettings? = null,
    val loadError: DomainError? = null,
    /** 광고·야간 동의를 바꾼 뒤 띄우는 처리 결과. 서비스 알림은 동의가 아니라 통지하지 않는다. */
    val notice: ConsentNotice? = null,
)

sealed interface NotificationSettingsEvent {
    data object LoadStarted : NotificationSettingsEvent

    data class Loaded(
        val settings: PushSettings,
    ) : NotificationSettingsEvent

    data class LoadFailed(
        val error: DomainError,
    ) : NotificationSettingsEvent

    /** 낙관 갱신·성공 확정·실패 되돌리기가 모두 같은 전이다. */
    data class SettingsChanged(
        val settings: PushSettings,
    ) : NotificationSettingsEvent

    data class NoticeShown(
        val notice: ConsentNotice,
    ) : NotificationSettingsEvent

    data object NoticeDismissed : NotificationSettingsEvent
}

sealed interface NotificationSettingsEffect {
    data object SaveFailed : NotificationSettingsEffect
}

/**
 * 알림 수신 동의 설정.
 *
 * 토글은 즉시 저장하고, 저장을 이 큐 안에서 기다린다. 그래서 앞 요청이 끝나기 전의 탭은 버려지지 않고
 * 순서대로 처리되며, 요청 직전의 상태가 곧 마지막으로 확인된 서버 값이라 실패 시 그 값으로 되돌린다.
 */
@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val repository: PushSettingsRepository,
    ) : MviViewModel<
            NotificationSettingsIntent,
            NotificationSettingsUiState,
            NotificationSettingsEvent,
            NotificationSettingsEffect,
        >(NotificationSettingsUiState()) {
        override suspend fun handleIntent(intent: NotificationSettingsIntent) {
            when (intent) {
                NotificationSettingsIntent.Load -> if (uiState.value.settings == null) load()
                NotificationSettingsIntent.Retry -> load()
                is NotificationSettingsIntent.Toggle -> toggle(intent.kind)
                NotificationSettingsIntent.DismissNotice -> dispatchEvent(NotificationSettingsEvent.NoticeDismissed)
            }
        }

        override fun reduce(
            state: NotificationSettingsUiState,
            event: NotificationSettingsEvent,
        ): NotificationSettingsUiState =
            when (event) {
                NotificationSettingsEvent.LoadStarted -> state.copy(isLoading = true, loadError = null)
                is NotificationSettingsEvent.Loaded -> state.copy(isLoading = false, settings = event.settings)
                is NotificationSettingsEvent.LoadFailed -> state.copy(isLoading = false, loadError = event.error)
                is NotificationSettingsEvent.SettingsChanged -> state.copy(settings = event.settings)
                is NotificationSettingsEvent.NoticeShown -> state.copy(notice = event.notice)
                NotificationSettingsEvent.NoticeDismissed -> state.copy(notice = null)
            }

        private suspend fun load() {
            dispatchEvent(NotificationSettingsEvent.LoadStarted)
            when (val result = repository.get()) {
                is DomainResult.Success -> dispatchEvent(NotificationSettingsEvent.Loaded(result.value))
                is DomainResult.Failure -> dispatchEvent(NotificationSettingsEvent.LoadFailed(result.error))
            }
        }

        private suspend fun toggle(kind: PushSettingKind) {
            val current = uiState.value.settings ?: return
            val requested = current.toggled(kind)
            if (requested == current) return
            dispatchEvent(NotificationSettingsEvent.SettingsChanged(requested))
            when (val result = repository.update(requested)) {
                is DomainResult.Success -> {
                    dispatchEvent(NotificationSettingsEvent.SettingsChanged(result.value))
                    requested.consentChange(kind)?.let { change ->
                        // 일시는 서버 응답이 아니라 의사를 표시한 시점이다 — 통지가 알려야 하는 값이 그것이다.
                        val notice = ConsentNotice(change, System.currentTimeMillis())
                        dispatchEvent(NotificationSettingsEvent.NoticeShown(notice))
                    }
                }
                is DomainResult.Failure -> {
                    dispatchEvent(NotificationSettingsEvent.SettingsChanged(current))
                    dispatchEffect(NotificationSettingsEffect.SaveFailed)
                    // 앱이 미리 막는 조합이라 이 응답은 들고 있던 값이 서버와 어긋났다는 뜻이다.
                    if (result.error.isNightRequiresMarketing()) load()
                }
            }
        }
    }

/** 광고를 끄면 야간도 함께 내린다. 광고가 꺼진 채 야간만 켜는 요청은 서버가 400 으로 거절한다. */
private fun PushSettings.toggled(kind: PushSettingKind): PushSettings =
    when (kind) {
        PushSettingKind.SERVICE -> copy(servicePush = !servicePush)
        PushSettingKind.MARKETING ->
            if (marketingPush) {
                copy(marketingPush = false, marketingNightPush = false)
            } else {
                copy(marketingPush = true)
            }

        PushSettingKind.MARKETING_NIGHT ->
            if (marketingPush) copy(marketingNightPush = !marketingNightPush) else this
    }

/** 요청한 값이 동의 상태를 어느 쪽으로 바꿨는가. 서비스 알림은 동의가 아니다. */
private fun PushSettings.consentChange(kind: PushSettingKind): ConsentChange? =
    when (kind) {
        PushSettingKind.SERVICE -> null
        PushSettingKind.MARKETING -> if (marketingPush) ConsentChange.MARKETING_ON else ConsentChange.MARKETING_OFF
        PushSettingKind.MARKETING_NIGHT -> if (marketingNightPush) ConsentChange.NIGHT_ON else ConsentChange.NIGHT_OFF
    }

private fun DomainError.isNightRequiresMarketing(): Boolean =
    this is DomainError.Server && code == PushSettingsRepository.ERROR_NIGHT_PUSH_REQUIRES_MARKETING
