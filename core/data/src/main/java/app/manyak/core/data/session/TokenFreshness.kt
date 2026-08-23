package app.manyak.core.data.session

/** 저장된 토큰과 함께 원자 기록되는 만료 판정 근거. */
data class TokenAnchors(
    val expiresInSeconds: Long,
    val elapsedRealtimeAnchorMillis: Long,
    val wallClockAnchorMillis: Long,
    /** null 이면 저장 당시 `BOOT_COUNT` 를 읽지 못했다는 명시적 표식(UNAVAILABLE)이다. */
    val bootGeneration: Long?,
)

enum class TokenFreshness {
    /** 그대로 요청에 써도 된다. */
    FRESH,

    /** 요청을 보내기 **전에** 재발급한다. */
    NEEDS_REFRESH,
}

/**
 * 요청 전 만료 판정.
 *
 * 서버의 선택적 인증 경로는 만료 토큰을 401 로 거절하지 않고 익명으로 통과시킨다. 그래서 회원이 만든
 * 콘텐츠가 주인 없는 데이터로 저장될 수 있고, 401 이 없으니 반응형 재발급도 트리거되지 않는다.
 * 판정을 요청 전에 두는 이유가 이것이다.
 *
 * 두 시계의 최댓값만 쓰면 **재부팅 + 벽시계 과거 조작** 조합에서 둘 다 실제 경과보다 작아지므로,
 * 부팅 세대가 같다는 것이 확인된 경우에만 경과를 신뢰한다.
 */
object TokenFreshnessEvaluator {
    const val REFRESH_MARGIN_MILLIS = 60_000L

    private const val MILLIS_PER_SECOND = 1_000L

    fun evaluate(
        anchors: TokenAnchors?,
        now: ClockSnapshot,
        anchorVerifiedInThisProcess: Boolean,
    ): TokenFreshness {
        val elapsed =
            anchors
                ?.takeIf { canTrustElapsed(it, now, anchorVerifiedInThisProcess) }
                ?.let { trustedElapsedMillis(it, now) }
                ?: return TokenFreshness.NEEDS_REFRESH

        val remaining = anchors.expiresInSeconds * MILLIS_PER_SECOND - elapsed
        return if (remaining > REFRESH_MARGIN_MILLIS) TokenFreshness.FRESH else TokenFreshness.NEEDS_REFRESH
    }

    /**
     * 부팅 세대가 같다고 확인되면 경과를 신뢰한다. 확인할 수 없을 때는 이번 프로세스에서 직접 발급하고
     * 앵커한 경우에만 신뢰한다 — 그 플래그가 없으면 재부팅 여부를 증명할 수 없다.
     */
    private fun canTrustElapsed(
        anchors: TokenAnchors,
        now: ClockSnapshot,
        anchorVerifiedInThisProcess: Boolean,
    ): Boolean {
        val sameBoot =
            anchors.bootGeneration != null &&
                now.bootGeneration != null &&
                anchors.bootGeneration == now.bootGeneration
        val trustsProcessAnchor =
            anchorVerifiedInThisProcess &&
                (anchors.bootGeneration == null || now.bootGeneration == null)
        return sameBoot || trustsProcessAnchor
    }

    /** 음수 경과는 0 으로 보정하지 않는다. 그 앵커는 신뢰할 수 없다는 신호이므로 제외한다. */
    private fun trustedElapsedMillis(
        anchors: TokenAnchors,
        now: ClockSnapshot,
    ): Long? {
        val monotonic = now.elapsedRealtimeMillis - anchors.elapsedRealtimeAnchorMillis
        val wallClock = now.wallClockMillis - anchors.wallClockAnchorMillis
        return listOf(monotonic, wallClock).filter { it >= 0 }.maxOrNull()
    }
}
