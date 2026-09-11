package app.manyak.notification.consent.presentation

import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.notification.consent.domain.MarketingConsentPromptRepository
import app.manyak.notification.consent.entity.ConsentChange
import app.manyak.notification.consent.entity.ConsentNotice
import app.manyak.notification.settings.domain.PushSettingsRepository
import app.manyak.notification.settings.entity.PushSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

sealed interface MarketingConsentIntent {
    /** 시트를 띄울 조건이 갖춰졌다. 물은 적이 없고 서버가 미동의일 때만 시트가 된다. */
    data object Prepare : MarketingConsentIntent

    data object Accept : MarketingConsentIntent

    data object Decline : MarketingConsentIntent

    data object DismissNotice : MarketingConsentIntent
}

data class MarketingConsentUiState(
    /** 서버에서 읽은 현재 동의. 값이 있는 동안 시트가 보인다 — 저장은 세 값을 전체 교체라 서비스 값이 필요하다. */
    val settings: PushSettings? = null,
    val isSubmitting: Boolean = false,
    /** 동의 저장 뒤 띄우는 처리 결과. 시트가 내려간 뒤 이어서 보인다. */
    val notice: ConsentNotice? = null,
) {
    val isSheetVisible: Boolean get() = settings != null
}

sealed interface MarketingConsentEvent {
    data class Ready(
        val settings: PushSettings,
    ) : MarketingConsentEvent

    data object SubmitStarted : MarketingConsentEvent

    data object SubmitFailed : MarketingConsentEvent

    data class Accepted(
        val notice: ConsentNotice,
    ) : MarketingConsentEvent

    data object Closed : MarketingConsentEvent

    data object NoticeDismissed : MarketingConsentEvent
}

sealed interface MarketingConsentEffect {
    data object SaveFailed : MarketingConsentEffect
}

/**
 * 첫 진입에서 광고 알림 동의를 한 번 묻는다.
 *
 * "물었다" 는 사용자가 답한 뒤에 기록한다 — 시트가 뜬 채 프로세스가 죽으면 다음 실행에서 다시 뜨는 편이,
 * 답하지 못한 질문을 물은 것으로 치는 것보다 낫다. 회전은 ViewModel 이 살아 있어 두 번 묻지 않는다.
 */
@HiltViewModel
class MarketingConsentViewModel
    @Inject
    constructor(
        private val promptRepository: MarketingConsentPromptRepository,
        private val settingsRepository: PushSettingsRepository,
    ) : MviViewModel<MarketingConsentIntent, MarketingConsentUiState, MarketingConsentEvent, MarketingConsentEffect>(
            MarketingConsentUiState(),
        ) {
        private var prepared = false

        override suspend fun handleIntent(intent: MarketingConsentIntent) {
            when (intent) {
                MarketingConsentIntent.Prepare -> prepare()
                MarketingConsentIntent.Accept -> accept()
                MarketingConsentIntent.Decline -> {
                    promptRepository.markPrompted()
                    dispatchEvent(MarketingConsentEvent.Closed)
                }
                MarketingConsentIntent.DismissNotice -> dispatchEvent(MarketingConsentEvent.NoticeDismissed)
            }
        }

        override fun reduce(
            state: MarketingConsentUiState,
            event: MarketingConsentEvent,
        ): MarketingConsentUiState =
            when (event) {
                is MarketingConsentEvent.Ready -> state.copy(settings = event.settings)
                MarketingConsentEvent.SubmitStarted -> state.copy(isSubmitting = true)
                MarketingConsentEvent.SubmitFailed -> state.copy(isSubmitting = false)
                is MarketingConsentEvent.Accepted ->
                    state.copy(settings = null, isSubmitting = false, notice = event.notice)
                MarketingConsentEvent.Closed -> state.copy(settings = null)
                MarketingConsentEvent.NoticeDismissed -> state.copy(notice = null)
            }

        /** 조회 실패는 조용히 넘긴다 — 다음 실행이 다시 시도하고, 그 사이에도 설정 화면에서 켤 수 있다. */
        private suspend fun prepare() {
            if (prepared) return
            prepared = true
            if (promptRepository.wasPrompted()) return
            val result = settingsRepository.get() as? DomainResult.Success ?: return
            if (!result.value.marketingPush) dispatchEvent(MarketingConsentEvent.Ready(result.value))
        }

        private suspend fun accept() {
            val current = uiState.value.settings ?: return
            if (uiState.value.isSubmitting) return
            dispatchEvent(MarketingConsentEvent.SubmitStarted)
            // 야간은 별도 동의라 여기서 함께 켜지 않는다 — 알림 설정에서 따로 켠다.
            val requested = current.copy(marketingPush = true)
            when (settingsRepository.update(requested)) {
                is DomainResult.Success -> {
                    promptRepository.markPrompted()
                    val notice = ConsentNotice(ConsentChange.MARKETING_ON, System.currentTimeMillis())
                    dispatchEvent(MarketingConsentEvent.Accepted(notice))
                }

                is DomainResult.Failure -> {
                    dispatchEvent(MarketingConsentEvent.SubmitFailed)
                    dispatchEffect(MarketingConsentEffect.SaveFailed)
                }
            }
        }
    }
