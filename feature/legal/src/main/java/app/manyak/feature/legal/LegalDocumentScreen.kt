package app.manyak.feature.legal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.manyak.core.navigation.LegalDocument
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.component.rememberDelayedProgressVisibility
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 약관·개인정보처리방침을 웹 페이지 그대로 보여 준다.
 *
 * 같은 호스트 밖으로는 이동하지 않는다 — 법적 문서 화면에서 임의의 목적지로 새는 경로를 만들지 않는다
 */
@Composable
fun LegalDocumentScreen(
    document: LegalDocument,
    onLeaveDocument: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LegalViewModel =
        hiltViewModel<LegalViewModel, LegalViewModel.Factory>(
            creationCallback = { factory -> factory.create(document) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showLoadingIndicator = rememberDelayedProgressVisibility(inProgress = state.isLoading)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ManyakTheme.colors.surface,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            LegalWebView(
                url = state.url,
                allowedHost = state.allowedHost,
                documentPath = state.documentPath,
                onLeaveDocument = onLeaveDocument,
                reloadToken = state.reloadToken,
                onPageStarted = { },
                onPageFinished = { viewModel.onIntent(LegalIntent.PageFinished) },
                onPageFailed = { viewModel.onIntent(LegalIntent.PageFailed) },
            )
            when {
                state.hasError ->
                    LegalLoadFailure(
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = { viewModel.onIntent(LegalIntent.Retry) },
                    )

                showLoadingIndicator -> ManyakProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LegalLoadFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(ManyakTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Text(
            text = stringResource(R.string.legal_load_failed),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textSubtle,
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.common_retry), style = ManyakTheme.typography.labelLarge)
        }
    }
}

// JavaScript 없이는 웹 문서가 렌더되지 않는다. 로드 대상은 우리 도메인 하나로 제한한다.
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LegalWebView(
    url: String,
    allowedHost: String?,
    documentPath: String?,
    onLeaveDocument: () -> Unit,
    reloadToken: Int,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onPageFailed: () -> Unit,
) {
    val pageState = rememberSaveable(saver = LegalPageStateSaver) { LegalPageState() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // Compose 안에서 초기 측정이 0 이면 뷰포트 단위(svh·vh)가 0 으로 굳는다.
                layoutParams =
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) = onPageStarted()

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) = onPageFinished()

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) onPageFailed()
                        }

                        // 우리 도메인 밖으로는 이동하지 않는다. 법적 문서 화면이 임의의 목적지로 새면 안 된다.
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = request?.url?.host != allowedHost

                        /**
                         * 웹 페이지 자체의 헤더 뒤로가기는 SPA 라우팅이라 페이지 로드가 일어나지 않는다.
                         * 그래서 [shouldOverrideUrlLoading] 으로는 막을 수 없고, 이 콜백으로 이탈을 감지해
                         * 화면을 닫는다 — 법적 문서에서 다른 제품 화면이 열리면 안 된다.
                         */
                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            if (isReload || url == null) return
                            val visited = url.toUri()
                            if (visited.host != allowedHost || visited.path != documentPath) {
                                onLeaveDocument()
                            }
                        }
                    }
            }
        },
        update = { webView ->
            pageState.webView = webView
            if (webView.tag != reloadToken) {
                webView.tag = reloadToken
                // 저장해 둔 히스토리가 있으면 그것으로 되살린다. 복원은 읽던 위치까지 돌려주지만
                // 재시도로 토큰이 오른 경우에는 저장분이 이미 비어 있어 새로 읽는다.
                val restored = pageState.restored
                pageState.restored = null
                if (restored == null || webView.restoreState(restored) == null) {
                    webView.loadUrl(url)
                }
            }
        },
        onRelease = { pageState.webView = null },
    )
}

/**
 * 구성 변경에서 [WebView] 는 새로 만들어진다. 히스토리를 넘겨주지 않으면 문서를 처음부터 다시 읽고
 * 읽던 위치가 사라진다.
 *
 * 저장을 [Saver] 에 맡기는 이유는 시점 때문이다 — 저장은 컴포지션이 걷히기 전에 일어나야 하는데,
 * `onRelease` 는 이미 걷힌 뒤라 그때 뜨면 늦는다.
 */
private class LegalPageState {
    var webView: WebView? = null
    var restored: Bundle? = null
}

private val LegalPageStateSaver: Saver<LegalPageState, Bundle> =
    Saver(
        save = { state -> state.webView?.let { webView -> Bundle().also(webView::saveState) } ?: state.restored },
        restore = { bundle -> LegalPageState().apply { restored = bundle } },
    )

/** 화면 제목. 라우트 인자만으로 결정되므로 호스트가 직접 읽는다. */
fun LegalDocument.titleRes(): Int =
    when (this) {
        LegalDocument.TERMS -> R.string.legal_terms_title
        LegalDocument.PRIVACY -> R.string.legal_privacy_title
    }
