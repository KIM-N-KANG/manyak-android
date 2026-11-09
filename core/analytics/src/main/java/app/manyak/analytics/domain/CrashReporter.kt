package app.manyak.analytics.domain

/**
 * 크래시 리포트에 붙는 진단 맥락.
 *
 * 화면과 ViewModel 은 이 계약을 직접 부르지 않는다 — 분석 퍼널이 이미 모든 이벤트를 지나가므로
 * breadcrumb 도 거기서 함께 남긴다. 화면이 같은 사실을 두 번 알리게 하면 한쪽만 빠뜨린다.
 *
 * 남기는 것은 이벤트·화면 이름과 공개 식별자뿐이다. 사용자 입력·토큰·링크 코드·URL 원문은 넣지 않는다.
 */
interface CrashReporter {
    fun recordEvent(
        name: String,
        screenName: String,
        properties: Map<String, Any?>,
    )

    /** null 이면 빈 값으로 지운다. 이후 리포트의 귀속만 끊고 이미 올라간 리포트는 남는다. */
    fun setUser(userId: String?)
}
