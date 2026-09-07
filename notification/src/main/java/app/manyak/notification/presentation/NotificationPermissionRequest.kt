package app.manyak.notification.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** 프로세스 수명 플래그. 구성 변경으로 Activity 가 재생성돼도 같은 프로세스에서는 다시 묻지 않는다. */
private var requestedInThisProcess = false

/**
 * 회원 그래프가 처음 그려질 때 한 번 시스템 알림 권한을 요청한다.
 *
 * 사전 설명·재요청 UI 는 두지 않는다 — 시스템이 두 번 거부 뒤 자동 거부하므로 횟수를 저장할 이유가 없고,
 * 거부해도 토큰 등록과 화면은 그대로다. 이후 재진입은 설정 화면의 시스템 설정 이동이 맡는다.
 */
@Composable
fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 결과에 따라 할 일이 없다. 권한은 표시 시점에 OS 가 다시 판정한다.
        }
    LaunchedEffect(Unit) {
        if (requestedInThisProcess) return@LaunchedEffect
        requestedInThisProcess = true
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
