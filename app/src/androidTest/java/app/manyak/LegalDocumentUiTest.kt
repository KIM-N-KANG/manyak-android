package app.manyak

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.core.navigation.LegalDocument
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.legal.domain.LegalUrlProvider
import app.manyak.legal.presentation.LegalDocumentScreen
import app.manyak.legal.presentation.LegalViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class LegalDocumentUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun externalWebLinkLaunchesBrowserAndInternalHomeIsBlocked() {
        val viewModel =
            LegalViewModel(
                LegalDocument.ABOUT,
                object : LegalUrlProvider {
                    override fun urlFor(document: LegalDocument) = "https://manyak.app/about"
                },
                NoOpAnalytics,
            )
        lateinit var root: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            ManyakTheme { LegalDocumentScreen(LegalDocument.ABOUT, onBack = {}, viewModel = viewModel) }
        }
        lateinit var web: WebView
        compose.runOnIdle {
            web = requireNotNull(findWebView(root))
            web.stopLoading()
            web.loadDataWithBaseURL(
                "https://manyak.app/about",
                """<html><head><title>fixture</title></head><body>
                <a id="internal" href="https://manyak.app/">Home</a>
                <a id="external" href="https://example.com/help">External</a>
                </body></html>""",
                "text/html",
                "UTF-8",
                null,
            )
        }
        val ready = AtomicReference(false)
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                web.evaluateJavascript("Boolean(document.getElementById('external'))") { ready.set(it == "true") }
            }
            ready.get()
        }
        var initialUrl: String? = null
        compose.runOnIdle { initialUrl = web.url }
        val launched = AtomicReference<Intent>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_VIEW) return null
                    launched.set(intent)
                    return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
                }
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.addMonitor(monitor)
        try {
            compose.runOnIdle { web.evaluateJavascript("document.getElementById('internal').click()", null) }
            compose.runOnIdle { assertEquals(initialUrl, web.url) }
            compose.runOnIdle { web.evaluateJavascript("document.getElementById('external').click()", null) }
            compose.waitUntil(10_000) { launched.get() != null }
            assertEquals("https://example.com/help", launched.get().dataString)
            assertEquals(true, launched.get().hasCategory(Intent.CATEGORY_BROWSABLE))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}

private fun findWebView(view: View): WebView? {
    if (view is WebView) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}
