package app.manyak.auth.data.session

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 실제 기기의 시계를 읽는다.
 *
 * `BOOT_COUNT` 는 API 24 부터 있고 제조사에 따라 읽히지 않을 수 있다. 읽지 못하면 0 으로 대체하지 않고
 * null 을 돌려준다 — 값이 0 인 기기와 읽지 못한 기기를 같게 다루면 재부팅을 감지하지 못한다.
 */
@Singleton
class AndroidSessionClock
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SessionClock {
        override fun now(): ClockSnapshot =
            ClockSnapshot(
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                wallClockMillis = System.currentTimeMillis(),
                bootGeneration = readBootCount(),
            )

        private fun readBootCount(): Long? =
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    null
                } else {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT).toLong()
                }
            }.getOrNull()
    }
