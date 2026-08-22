package app.manyak.core.data.session

/**
 * 만료 판정이 읽는 시계 값의 스냅숏.
 *
 * [bootGeneration]이 null 이면 `Settings.Global.BOOT_COUNT` 를 읽지 못했다는 뜻이며,
 * 값이 없다는 사실과 값이 0 이라는 사실을 구분하기 위해 표식으로 남긴다.
 */
data class ClockSnapshot(
    val elapsedRealtimeMillis: Long,
    val wallClockMillis: Long,
    val bootGeneration: Long?,
)

/** 테스트에서 시계를 갈아 끼울 수 있도록 읽기를 인터페이스로 분리한다. */
interface SessionClock {
    fun now(): ClockSnapshot
}
