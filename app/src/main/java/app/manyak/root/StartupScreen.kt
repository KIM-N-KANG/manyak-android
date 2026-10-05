package app.manyak.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.manyak.designsystem.theme.ManyakTheme

/** 서버 확인 동안 로그인 화면을 노출하지 않는 앱 시작 화면. 오류는 같은 화면에서 복구한다. */
@Composable
internal fun StartupScreen(
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    failureContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(ManyakTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.block, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BouncingLogo(bouncing = showProgress && failureContent == null)
        failureContent?.invoke()
    }
}

@Preview(showBackground = true)
@Composable
private fun StartupScreenPreview() {
    ManyakTheme {
        StartupScreen(showProgress = true)
    }
}
