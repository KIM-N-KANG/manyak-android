package app.manyak.feature.legal

import android.net.Uri
import app.manyak.core.analytics.Analytics
import app.manyak.core.analytics.AnalyticsEvent
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.ui.mvi.MviViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

sealed interface LegalIntent {
    data object PageFinished : LegalIntent

    data object PageFailed : LegalIntent

    data object Retry : LegalIntent
}

data class LegalUiState(
    val document: LegalDocument,
    val url: String,
    /** 이 호스트 밖으로는 이동하지 않는다. */
    val allowedHost: String?,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    /** 재시도할 때마다 올린다. WebView 는 이 값이 바뀌면 다시 읽는다. */
    val reloadToken: Int = 0,
)

sealed interface LegalEvent {
    data object Loaded : LegalEvent

    data object Failed : LegalEvent

    data object Reloading : LegalEvent
}

/**
 * 어느 문서를 여는지는 라우트가 정한다. 백스택의 키가 곧 그 값이므로 목적지에서 받아 넘긴다.
 */
@HiltViewModel(assistedFactory = LegalViewModel.Factory::class)
class LegalViewModel
    @AssistedInject
    constructor(
        @Assisted document: LegalDocument,
        urlProvider: LegalUrlProvider,
        analytics: Analytics,
    ) : MviViewModel<LegalIntent, LegalUiState, LegalEvent, Nothing>(
            initialState =
                urlProvider.urlFor(document).let { url ->
                    LegalUiState(
                        document = document,
                        url = url,
                        allowedHost = Uri.parse(url).host,
                    )
                },
        ) {
        @AssistedFactory
        interface Factory {
            fun create(document: LegalDocument): LegalViewModel
        }

        init {
            // 화면은 하나지만 screen_name 은 웹처럼 문서별로 갈라야 같은 필터로 볼 수 있다.
            analytics.track(
                when (document) {
                    LegalDocument.TERMS -> AnalyticsEvent.TermsViewed
                    LegalDocument.PRIVACY -> AnalyticsEvent.PrivacyViewed
                    LegalDocument.ABOUT -> AnalyticsEvent.ServiceInfoViewed
                },
            )
        }

        override suspend fun handleIntent(intent: LegalIntent) {
            when (intent) {
                LegalIntent.PageFinished -> dispatchEvent(LegalEvent.Loaded)
                LegalIntent.PageFailed -> dispatchEvent(LegalEvent.Failed)
                LegalIntent.Retry -> dispatchEvent(LegalEvent.Reloading)
            }
        }

        override fun reduce(
            state: LegalUiState,
            event: LegalEvent,
        ): LegalUiState =
            when (event) {
                LegalEvent.Loaded -> state.copy(isLoading = false, hasError = false)
                LegalEvent.Failed -> state.copy(isLoading = false, hasError = true)
                LegalEvent.Reloading ->
                    state.copy(isLoading = true, hasError = false, reloadToken = state.reloadToken + 1)
            }
    }
