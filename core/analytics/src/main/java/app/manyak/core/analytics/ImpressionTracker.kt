package app.manyak.core.analytics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay

/**
 * 목록 하나가 드는 노출 장부. 카드가 스크롤로 사라졌다 돌아와도 같은 항목은 30초 안에 다시 세지 않는다.
 * 카드 안에 두면 컴포저블이 버려질 때 함께 사라져 중복 제거가 되지 않는다.
 */
@Stable
class ImpressionTracker {
    private val lastReportedAt = HashMap<Any, Long>()

    internal fun shouldReport(key: Any): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastReportedAt[key]
        if (last != null && now - last < DEDUPE_WINDOW_MILLIS) return false
        lastReportedAt[key] = now
        return true
    }

    private companion object {
        const val DEDUPE_WINDOW_MILLIS = 30_000L
    }
}

@Composable
fun rememberImpressionTracker(): ImpressionTracker = remember { ImpressionTracker() }

/**
 * 면적의 절반 이상이 1초 넘게 창 안에 있으면 유효 노출로 본다.
 *
 * ponytail: onGloballyPositioned 는 스크롤 프레임마다 불린다. 목록이 수백 장으로 커지면
 * 가시 영역 계산을 LazyListState 기반으로 옮긴다.
 */
@Composable
fun Modifier.trackImpression(
    tracker: ImpressionTracker,
    key: Any,
    onImpressed: () -> Unit,
): Modifier {
    var visibleEnough by remember(key) { mutableStateOf(false) }
    val latestOnImpressed by rememberUpdatedState(onImpressed)
    LaunchedEffect(key, visibleEnough) {
        if (!visibleEnough) return@LaunchedEffect
        delay(DWELL_MILLIS)
        if (tracker.shouldReport(key)) latestOnImpressed()
    }
    return onGloballyPositioned { coordinates -> visibleEnough = coordinates.visibleFraction() >= MIN_VISIBLE_FRACTION }
}

private fun LayoutCoordinates.visibleFraction(): Float {
    val total = size.width.toFloat() * size.height.toFloat()
    if (total <= 0f || !isAttached) return 0f
    val bounds = boundsInWindow()
    return (bounds.width * bounds.height) / total
}

private const val DWELL_MILLIS = 1_000L
private const val MIN_VISIBLE_FRACTION = 0.5f
