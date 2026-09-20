package app.manyak.legal.consent.presentation

import androidx.lifecycle.viewModelScope
import app.manyak.auth.domain.SessionRepository
import app.manyak.auth.entity.SessionState
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.presentation.mvi.MviViewModel
import app.manyak.core.navigation.LegalDocument
import app.manyak.legal.consent.domain.ConsentRepository
import app.manyak.legal.consent.entity.ConsentItem
import app.manyak.legal.consent.entity.ConsentStatus
import app.manyak.legal.consent.entity.RequiredConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LegalConsentIntent {
    data class Toggle(
        val item: ConsentItem,
    ) : LegalConsentIntent

    /** 필수 항목과 선택 항목을 한 번에 켜고 끈다. */
    data object ToggleAll : LegalConsentIntent

    data object ToggleMarketing : LegalConsentIntent

    data object Submit : LegalConsentIntent

    data object Retry : LegalConsentIntent

    data class OpenDocument(
        val document: LegalDocument,
    ) : LegalConsentIntent

    data object CloseDocument : LegalConsentIntent

    /** 시트가 열린 채 뒤로가기 — 동의하지 않은 것으로 보고 로그아웃한다. */
    data object Abandon : LegalConsentIntent

    /** 루트가 선택 항목의 답을 알림 기능에 넘겼다. */
    data object MarketingAnswerConsumed : LegalConsentIntent
}

enum class LegalConsentPhase {
    /** 조회 중. 시트는 없지만 아직 만족으로 보지 않는다. */
    CHECKING,
    REQUIRED,
    SATISFIED,
    LOAD_FAILED,
}

enum class LegalConsentNotice {
    RETRYABLE,
    VERSION_MISMATCH,
}

data class LegalConsentUiState(
    val phase: LegalConsentPhase = LegalConsentPhase.CHECKING,
    val required: List<RequiredConsent> = emptyList(),
    val checked: Set<ConsentItem> = emptySet(),
    /** 선택 항목인 광고 알림 수신 동의. 필수 항목과 함께 체크만 받고 저장은 알림 기능이 한다. */
    val marketingOptIn: Boolean = false,
    /** 제출이 성공한 시점의 선택 항목 값. 루트가 알림 기능에 넘긴 뒤 비운다. */
    val marketingAnswer: Boolean? = null,
    val isSubmitting: Boolean = false,
    val isLoggingOut: Boolean = false,
    val notice: LegalConsentNotice? = null,
    val viewingDocument: LegalDocument? = null,
) {
    val isSatisfied: Boolean get() = phase == LegalConsentPhase.SATISFIED
    val isSheetVisible: Boolean
        get() = phase == LegalConsentPhase.REQUIRED || phase == LegalConsentPhase.LOAD_FAILED
    val isEveryRequiredChecked: Boolean get() = required.isNotEmpty() && required.all { it.item in checked }
    val isAllChecked: Boolean get() = isEveryRequiredChecked && marketingOptIn
    val isLocked: Boolean get() = isSubmitting || isLoggingOut
}

sealed interface LegalConsentEvent {
    data object Loading : LegalConsentEvent

    data class Loaded(
        val status: ConsentStatus,
    ) : LegalConsentEvent

    data object LoadFailed : LegalConsentEvent

    data class CheckedChanged(
        val checked: Set<ConsentItem>,
        val marketingOptIn: Boolean,
    ) : LegalConsentEvent

    data object SubmitStarted : LegalConsentEvent

    data class SubmitFailed(
        val notice: LegalConsentNotice,
    ) : LegalConsentEvent

    data class Satisfied(
        val marketingAnswer: Boolean,
    ) : LegalConsentEvent

    data object MarketingAnswerConsumed : LegalConsentEvent

    data object LoggingOut : LegalConsentEvent

    data class DocumentChanged(
        val document: LegalDocument?,
    ) : LegalConsentEvent

    data object Reset : LegalConsentEvent
}

/**
 * 로그인 직후 필수 동의를 판정하는 게이트. 완료 판정은 서버 저장 성공 하나다 — 로컬에 "시트를 보여 줬다" 를 남기지 않는다.
 *
 * 액티비티 수명이라 로그아웃 뒤 다른 회원이 로그인해도 살아 있으므로, 준비 플래그 대신 세션 상태를 보고
 * 회원이 될 때마다 다시 판정한다. 회원이 아니면 상태를 비워 이전 회원의 체크가 다음 회원에게 남지 않게 한다.
 */
@HiltViewModel
class LegalConsentViewModel
    @Inject
    constructor(
        private val consentRepository: ConsentRepository,
        private val sessionRepository: SessionRepository,
    ) : MviViewModel<LegalConsentIntent, LegalConsentUiState, LegalConsentEvent, Nothing>(LegalConsentUiState()) {
        private var loadJob: Job? = null

        init {
            viewModelScope.launch {
                sessionRepository.sessionState.collect { state ->
                    // 조회를 기다리지 않는다 — 기다리면 그 사이의 로그아웃·재로그인이 접혀 새 회원을 판정하지 못한다.
                    loadJob?.cancel()
                    if (state == SessionState.Member) {
                        loadJob = launch { load() }
                    } else {
                        dispatchEvent(LegalConsentEvent.Reset)
                    }
                }
            }
        }

        override suspend fun handleIntent(intent: LegalConsentIntent) {
            val state = uiState.value
            when (intent) {
                is LegalConsentIntent.Toggle -> toggle(state, intent.item)
                LegalConsentIntent.ToggleAll -> toggleAll(state)
                LegalConsentIntent.ToggleMarketing -> setChecked(state, state.checked, !state.marketingOptIn)
                LegalConsentIntent.Submit -> submit(state)
                LegalConsentIntent.Retry -> if (state.phase == LegalConsentPhase.LOAD_FAILED) load()
                is LegalConsentIntent.OpenDocument -> openDocument(state, intent.document)
                LegalConsentIntent.CloseDocument -> dispatchEvent(LegalConsentEvent.DocumentChanged(null))
                LegalConsentIntent.Abandon -> abandon(state)
                LegalConsentIntent.MarketingAnswerConsumed -> dispatchEvent(LegalConsentEvent.MarketingAnswerConsumed)
            }
        }

        override fun reduce(
            state: LegalConsentUiState,
            event: LegalConsentEvent,
        ): LegalConsentUiState =
            when (event) {
                LegalConsentEvent.Loading -> state.copy(phase = LegalConsentPhase.CHECKING)
                // 필요 항목이 바뀌었을 수 있어 체크는 항상 새로 받는다. 알림은 제출이 다시 시작될 때 지운다.
                is LegalConsentEvent.Loaded ->
                    state.copy(
                        phase =
                            if (event.status.isSatisfied) LegalConsentPhase.SATISFIED else LegalConsentPhase.REQUIRED,
                        required = event.status.required,
                        checked = emptySet(),
                        marketingOptIn = false,
                        isSubmitting = false,
                    )
                LegalConsentEvent.LoadFailed ->
                    state.copy(phase = LegalConsentPhase.LOAD_FAILED, required = emptyList())
                LegalConsentEvent.SubmitStarted -> state.copy(isSubmitting = true, notice = null)
                is LegalConsentEvent.SubmitFailed -> state.copy(isSubmitting = false, notice = event.notice)
                is LegalConsentEvent.Satisfied ->
                    LegalConsentUiState(phase = LegalConsentPhase.SATISFIED, marketingAnswer = event.marketingAnswer)
                LegalConsentEvent.LoggingOut -> state.copy(isLoggingOut = true)
                is LegalConsentEvent.DocumentChanged -> state.copy(viewingDocument = event.document)
                LegalConsentEvent.Reset -> LegalConsentUiState()
                else -> reduceSelection(state, event)
            }

        private fun reduceSelection(
            state: LegalConsentUiState,
            event: LegalConsentEvent,
        ): LegalConsentUiState =
            when (event) {
                is LegalConsentEvent.CheckedChanged ->
                    state.copy(checked = event.checked, marketingOptIn = event.marketingOptIn)
                LegalConsentEvent.MarketingAnswerConsumed -> state.copy(marketingAnswer = null)
                else -> state
            }

        private suspend fun load() {
            dispatchEvent(LegalConsentEvent.Loading)
            when (val result = consentRepository.get()) {
                is DomainResult.Success -> dispatchEvent(LegalConsentEvent.Loaded(result.value))
                // 401·403 은 세션 종료 흐름이 따로 처리한다. 그때까지는 재시도만 보여 주고 회원 기능을 열지 않는다.
                is DomainResult.Failure -> dispatchEvent(LegalConsentEvent.LoadFailed)
            }
        }

        private suspend fun toggle(
            state: LegalConsentUiState,
            item: ConsentItem,
        ) {
            val next = if (item in state.checked) state.checked - item else state.checked + item
            setChecked(state, next, state.marketingOptIn)
        }

        private suspend fun toggleAll(state: LegalConsentUiState) {
            val turnOn = !state.isAllChecked
            setChecked(state, if (turnOn) state.required.map { it.item }.toSet() else emptySet(), turnOn)
        }

        private suspend fun setChecked(
            state: LegalConsentUiState,
            checked: Set<ConsentItem>,
            marketingOptIn: Boolean,
        ) {
            if (state.isLocked) return
            dispatchEvent(LegalConsentEvent.CheckedChanged(checked, marketingOptIn))
        }

        private suspend fun openDocument(
            state: LegalConsentUiState,
            document: LegalDocument,
        ) {
            if (state.isLocked) return
            dispatchEvent(LegalConsentEvent.DocumentChanged(document))
        }

        private suspend fun submit(state: LegalConsentUiState) {
            if (state.isLocked || !state.isEveryRequiredChecked) return
            dispatchEvent(LegalConsentEvent.SubmitStarted)
            val versions = state.required.associate { it.item to it.requiredVersion }
            when (val result = consentRepository.record(versions)) {
                is DomainResult.Success ->
                    if (result.value.isSatisfied) {
                        dispatchEvent(LegalConsentEvent.Satisfied(state.marketingOptIn))
                    } else {
                        // 기록은 됐지만 다른 항목의 요구 버전이 바뀌었다. 같은 시트에서 다시 받는다.
                        dispatchEvent(LegalConsentEvent.SubmitFailed(LegalConsentNotice.VERSION_MISMATCH))
                        dispatchEvent(LegalConsentEvent.Loaded(result.value))
                    }

                is DomainResult.Failure ->
                    if (result.error.isVersionMismatch()) {
                        dispatchEvent(LegalConsentEvent.SubmitFailed(LegalConsentNotice.VERSION_MISMATCH))
                        load()
                    } else {
                        dispatchEvent(LegalConsentEvent.SubmitFailed(LegalConsentNotice.RETRYABLE))
                    }
            }
        }

        /** 로그아웃 완료는 기다리지 않는다. 세션이 바뀌면 루트가 회원 그래프째 걷어낸다. */
        private suspend fun abandon(state: LegalConsentUiState) {
            if (state.isLocked) return
            dispatchEvent(LegalConsentEvent.LoggingOut)
            sessionRepository.signOut()
        }
    }

private fun DomainError.isVersionMismatch(): Boolean =
    this is DomainError.Server && code == ConsentRepository.ERROR_VERSION_MISMATCH
