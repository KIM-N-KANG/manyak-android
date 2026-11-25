package app.manyak.auth.data.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 재부팅·벽시계 조작·`BOOT_COUNT` 읽기 실패 조합에서
 * 만료 토큰을 유효로 판정하지 않는지, 그러면서도 재발급 무한 루프에 빠지지 않는지 확인한다.
 */
class TokenFreshnessEvaluatorTest {
    private val anchors =
        TokenAnchors(
            expiresInSeconds = 1_800,
            elapsedRealtimeAnchorMillis = 10_000,
            wallClockAnchorMillis = 1_000_000,
            bootGeneration = 7,
        )

    @Test
    fun `같은 부팅에서 여유가 남으면 그대로 쓴다`() {
        val now = snapshot(elapsed = 10_000 + 600_000, wallClock = 1_000_000 + 600_000, boot = 7)

        assertEquals(TokenFreshness.FRESH, evaluate(anchors, now))
    }

    @Test
    fun `만료 60초 전부터는 요청 전에 재발급한다`() {
        val justInsideMargin = anchors.expiresInSeconds * 1_000 - TokenFreshnessEvaluator.REFRESH_MARGIN_MILLIS
        val now = snapshot(elapsed = 10_000 + justInsideMargin, wallClock = 1_000_000 + justInsideMargin, boot = 7)

        assertEquals(TokenFreshness.NEEDS_REFRESH, evaluate(anchors, now))
    }

    @Test
    fun `벽시계를 과거로 돌려도 단조 경과로 만료를 판정한다`() {
        val now = snapshot(elapsed = 10_000 + 1_799_000, wallClock = 500_000, boot = 7)

        assertEquals(TokenFreshness.NEEDS_REFRESH, evaluate(anchors, now))
    }

    @Test
    fun `재부팅과 벽시계 롤백이 겹치면 경과가 작아 보여도 재발급한다`() {
        // 재부팅으로 단조 시계가 0 부터 다시 세고, 벽시계도 과거로 돌아가 두 경과가 모두 작다.
        val now = snapshot(elapsed = 5_000, wallClock = 900_000, boot = 8)

        assertEquals(TokenFreshness.NEEDS_REFRESH, evaluate(anchors, now))
    }

    @Test
    fun `부팅 세대를 읽지 못하면 이번 프로세스에서 앵커하기 전까지 재발급한다`() {
        val unavailable = anchors.copy(bootGeneration = null)
        val now = snapshot(elapsed = 10_000 + 1_000, wallClock = 1_000_000 + 1_000, boot = null)

        assertEquals(TokenFreshness.NEEDS_REFRESH, evaluate(unavailable, now))
    }

    @Test
    fun `읽지 못하는 기기라도 이번 프로세스에서 발급했으면 반복 재발급하지 않는다`() {
        val unavailable = anchors.copy(bootGeneration = null)
        val now = snapshot(elapsed = 10_000 + 1_000, wallClock = 1_000_000 + 1_000, boot = null)

        assertEquals(TokenFreshness.FRESH, evaluate(unavailable, now, verifiedInThisProcess = true))
    }

    @Test
    fun `두 앵커가 모두 미래면 신뢰하지 않고 재발급한다`() {
        val now = snapshot(elapsed = 9_000, wallClock = 999_000, boot = 7)

        assertEquals(TokenFreshness.NEEDS_REFRESH, evaluate(anchors, now))
    }

    @Test
    fun `저장된 레코드가 없으면 재발급한다`() {
        val now = snapshot(elapsed = 1, wallClock = 1, boot = 1)

        assertEquals(TokenFreshness.NEEDS_REFRESH, evaluate(null, now, verifiedInThisProcess = true))
    }

    private fun snapshot(
        elapsed: Long,
        wallClock: Long,
        boot: Long?,
    ) = ClockSnapshot(elapsedRealtimeMillis = elapsed, wallClockMillis = wallClock, bootGeneration = boot)

    private fun evaluate(
        anchors: TokenAnchors?,
        now: ClockSnapshot,
        verifiedInThisProcess: Boolean = false,
    ) = TokenFreshnessEvaluator.evaluate(anchors, now, anchorVerifiedInThisProcess = verifiedInThisProcess)
}
