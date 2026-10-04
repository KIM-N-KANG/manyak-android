package app.manyak.legal.presentation

import java.net.URI

internal enum class LegalNavigation {
    DOCUMENT,
    EXTERNAL,
    BLOCKED,
}

/** 문서가 아닌 같은 사이트의 화면과 실행 가능한 URI는 WebView에서 열지 않는다. */
internal fun legalNavigation(
    url: String,
    allowedHost: String?,
): LegalNavigation {
    val uri = runCatching { URI(url) }.getOrNull() ?: return LegalNavigation.BLOCKED
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("https", "http") || uri.host == null || uri.userInfo != null) {
        return LegalNavigation.BLOCKED
    }
    if (!uri.host.equals(allowedHost, ignoreCase = true)) return LegalNavigation.EXTERNAL
    return if (scheme == "https" && uri.path.trimEnd('/') in setOf("/terms", "/privacy", "/about")) {
        LegalNavigation.DOCUMENT
    } else {
        LegalNavigation.BLOCKED
    }
}
