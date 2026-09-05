package app.manyak.feature.login

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.manyak.designsystem.theme.ManyakTheme
import kotlinx.coroutines.delay

/**
 * 로그인 화면 배경. 이미지를 천천히 교차 전환하고, 버튼과 약관 문구가 놓이는 아래쪽은 표면색으로
 * 덮어 대비를 확보한다.
 */
@Composable
internal fun LoginBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        CrossfadingImages(modifier = Modifier.fillMaxSize())
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(bottomScrim(ManyakTheme.colors.surface)),
        )
    }
}

@Composable
private fun CrossfadingImages(modifier: Modifier = Modifier) {
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var incomingIndex by remember { mutableStateOf<Int?>(null) }
    val incomingAlpha = remember { Animatable(0f) }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val context = LocalContext.current
    val motionReduced = remember(context) { context.isMotionReduced() }
    // 카카오 로그인은 외부 앱으로 나간다. 화면이 컴포지션에 남아 있어도 안 보이는 동안은 돌리지 않는다.
    val playing = !motionReduced && lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        // 전환 도중 멈췄을 수 있어 항상 정지 상태에서 다시 시작한다.
        incomingIndex = null
        incomingAlpha.snapTo(0f)
        while (true) {
            delay(HOLD_DURATION_MILLIS)
            val next = (currentIndex + 1) % LoginBackgroundImages.size
            incomingIndex = next
            incomingAlpha.animateTo(1f, tween(TRANSITION_DURATION_MILLIS))
            currentIndex = next
            incomingIndex = null
            incomingAlpha.snapTo(0f)
        }
    }

    // 겹치는 순간에도 농도가 일정하도록 개별 이미지가 아니라 묶음에 한 번만 alpha 를 건다. 이미지마다
    // 걸면 전환 중 두 장의 반투명이 합쳐져 밝기가 출렁인다.
    Box(modifier = modifier.graphicsLayer { alpha = IMAGE_ALPHA }) {
        // 나가는 장을 불투명하게 유지하고 들어오는 장만 덮는다. 양쪽을 동시에 페이드하면 중간에 표면색이
        // 비쳐 화면이 한 번 밝아진다.
        BackgroundImage(imageRes = LoginBackgroundImages[currentIndex], alpha = 1f)
        incomingIndex?.let { BackgroundImage(imageRes = LoginBackgroundImages[it], alpha = incomingAlpha.value) }
    }
}

@Composable
private fun BackgroundImage(
    imageRes: Int,
    alpha: Float,
) {
    Image(
        modifier = Modifier.fillMaxSize(),
        painter = painterResource(imageRes),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
    )
}

private fun bottomScrim(surface: Color): Brush =
    Brush.verticalGradient(
        SCRIM_START to surface.copy(alpha = 0f),
        SCRIM_END to surface.copy(alpha = SCRIM_ALPHA),
        1f to surface.copy(alpha = SCRIM_ALPHA),
    )

private fun Context.isMotionReduced(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private val LoginBackgroundImages =
    listOf(
        R.drawable.bg_login_1,
        R.drawable.bg_login_2,
        R.drawable.bg_login_3,
        R.drawable.bg_login_4,
        R.drawable.bg_login_5,
    )

/** 배경 농도. 이 위에 올라가는 본문 대비를 확보하려고 원본보다 크게 낮춘다. */
private const val IMAGE_ALPHA = 0.4f

/** 배경이 가장 어두울 때에도 약관 문구가 4.5:1 을 넘도록 잡은 하단 표면 농도. */
private const val SCRIM_ALPHA = 0.85f
private const val SCRIM_START = 0.42f
private const val SCRIM_END = 0.78f

private const val HOLD_DURATION_MILLIS = 4_000L
private const val TRANSITION_DURATION_MILLIS = 1_200
