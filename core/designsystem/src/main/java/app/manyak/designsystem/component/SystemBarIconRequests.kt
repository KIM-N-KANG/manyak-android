package app.manyak.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

/** 밝은 아이콘을 요청한 화면 수. 화면이 컨트롤러를 직접 바꾸면 루트의 테마 반영과 실행 순서를 다투게 된다. */
@Stable
class SystemBarIconRequests internal constructor() {
    internal var lightStatusBars by mutableIntStateOf(0)
    internal var lightNavigationBars by mutableIntStateOf(0)
}

val LocalSystemBarIconRequests = staticCompositionLocalOf { SystemBarIconRequests() }

/**
 * 시스템 바 아이콘 밝기를 앱 테마와 화면 요청을 합쳐 한 곳에서 적용한다. 반환값을 [LocalSystemBarIconRequests] 로 내린다.
 * edge-to-edge 기본값은 **시스템** 다크 모드를 따르므로, 앱에서 테마를 강제하면 상태 바 시계와 배터리가
 * 배경과 같은 밝기가 되어 읽히지 않는다.
 */
@Composable
fun rememberSystemBarIconRequests(darkTheme: Boolean): SystemBarIconRequests {
    val requests = remember { SystemBarIconRequests() }
    val view = LocalView.current
    val lightStatusIcons = darkTheme || requests.lightStatusBars > 0
    val lightNavigationIcons = darkTheme || requests.lightNavigationBars > 0
    SideEffect {
        ViewCompat.getWindowInsetsController(view)?.run {
            isAppearanceLightStatusBars = !lightStatusIcons
            isAppearanceLightNavigationBars = !lightNavigationIcons
        }
    }
    return requests
}

/** 어두운 배경 위에 있는 동안 시스템 바 아이콘을 밝게 둔다. 화면을 벗어나면 앱 테마를 다시 따른다. */
@Composable
fun LightSystemBarIcons(navigationBars: Boolean = false) {
    val requests = LocalSystemBarIconRequests.current
    DisposableEffect(requests, navigationBars) {
        requests.lightStatusBars++
        if (navigationBars) requests.lightNavigationBars++
        onDispose {
            requests.lightStatusBars--
            if (navigationBars) requests.lightNavigationBars--
        }
    }
}
