package app.manyak.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 마냑 로고 락업. 높이만 토큰으로 고정하고 폭은 원본 비율로 따라간다.
 *
 * 비율 상수를 화면마다 적으면 한 화면에서만 로고가 찌그러져도 알아채기 어렵다.
 */
@Composable
fun ManyakLogo(modifier: Modifier = Modifier) {
    Image(
        modifier =
            modifier
                .height(ManyakTheme.sizes.logo)
                .aspectRatio(LOGO_ASPECT_RATIO),
        painter = painterResource(R.drawable.ic_logo_manyak),
        contentDescription = stringResource(R.string.app_logo_description),
    )
}

/** 로고 원본(89×32)의 가로세로 비율. */
private const val LOGO_ASPECT_RATIO = 89f / 32f
