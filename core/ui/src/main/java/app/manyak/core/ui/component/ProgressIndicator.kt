package app.manyak.core.ui.component

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.manyak.core.ui.theme.ManyakTheme
import kotlinx.coroutines.delay

/**
 * 앱의 모든 로딩 스피너. 색을 호출부마다 지정하면 화면끼리 어긋난다.
 *
 * 크기와 정렬은 놓이는 자리마다 다르므로 [modifier] 로 받는다.
 */
@Composable
fun ManyakProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier, color = ManyakTheme.colors.progressIndicator)
}

/**
 * [inProgress] 가 [appearAfterMillis] 보다 짧게 유지되면 계속 false 다 — 금방 끝나는 작업에서
 * 스피너가 스쳐 지나가는 깜빡임을 없앤다.
 *
 * 탭에 대한 즉각 피드백처럼 지연이 무반응으로 보이는 자리에는 쓰지 않는다.
 */
@Composable
fun rememberDelayedProgressVisibility(
    inProgress: Boolean,
    appearAfterMillis: Long = PROGRESS_APPEAR_AFTER_MILLIS,
): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(inProgress) {
        if (inProgress) {
            delay(appearAfterMillis)
            shown = true
        } else {
            shown = false
        }
    }
    return shown
}

/** 이보다 빨리 끝나는 작업은 스피너를 띄우지 않는다. */
private const val PROGRESS_APPEAR_AFTER_MILLIS = 300L
