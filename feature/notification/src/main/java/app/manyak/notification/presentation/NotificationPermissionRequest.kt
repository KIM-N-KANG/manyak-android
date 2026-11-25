package app.manyak.notification.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 회원 그래프가 처음 그려질 때 **설치당 한 번** 시스템 알림 권한을 요청한다.
 *
 * 사전 설명·재요청 UI 는 두지 않는다 — 거부는 사용자의 결정이고, 이후 재진입은 알림 설정 화면의 배너가
 * 시스템 설정으로 보낸다. 거부해도 토큰 등록과 화면은 그대로다. 이미 허용된 상태여도 물었다고 기록해,
 * 나중에 사용자가 시스템에서 끄더라도 앱이 다시 묻지 않는다.
 *
 * @param onSettled 더 물을 것이 없어진 시점 — 묻지 않았거나, 물었고 답이 돌아왔다. 결과는 넘기지 않는다;
 * 권한은 표시 시점에 OS 가 다시 판정한다. 뒤이어 뜨는 안내가 시스템 다이얼로그와 겹치지 않게 하는 신호다.
 */
@Composable
fun NotificationPermissionRequest(
    onSettled: () -> Unit,
    viewModel: NotificationPermissionViewModel = hiltViewModel(),
) {
    val settled by rememberUpdatedState(onSettled)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LaunchedEffect(viewModel) { settled() }
        return
    }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { settled() }
    LaunchedEffect(viewModel) {
        if (!viewModel.claimPrompt()) {
            settled()
            return@LaunchedEffect
        }
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) settled() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
