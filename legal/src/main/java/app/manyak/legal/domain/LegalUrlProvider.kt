package app.manyak.legal.domain

import app.manyak.core.navigation.LegalDocument

/**
 * 법적 문서의 웹 주소. 구현은 `BuildConfig` 를 가진 `:app` 이 제공한다.
 *
 * 앱은 본문을 복제하지 않고 웹 페이지를 그대로 연다 — 본문 정본이 웹 한 곳이라 복제하면 시행일·버전이
 * 갈라진다(docs/plans/login.md 결정 1).
 */
interface LegalUrlProvider {
    fun urlFor(document: LegalDocument): String
}
