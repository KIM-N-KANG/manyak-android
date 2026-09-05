package app.manyak.feature.my

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.core.ui.R
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

/** 제공자 로고와 표시 이름의 단일 매핑. 칩·다이얼로그·토스트가 같은 값을 쓴다. */
@get:DrawableRes
internal val AuthProvider.logoRes: Int
    get() =
        when (this) {
            AuthProvider.GOOGLE -> DesignsystemR.drawable.ic_logo_google
            AuthProvider.KAKAO -> DesignsystemR.drawable.ic_logo_kakao
        }

@get:StringRes
internal val AuthProvider.labelRes: Int
    get() =
        when (this) {
            AuthProvider.GOOGLE -> R.string.my_provider_google
            AuthProvider.KAKAO -> R.string.my_provider_kakao
        }

/**
 * 노란 컨테이너 없이 칩 안에 놓일 때의 로고 색.
 *
 * 카카오 말풍선은 노랑 위에서만 검정이다. 칩에는 컨테이너가 없어 검정 그대로 두면 다크 모드에서
 * 배경에 묻히므로, 카카오가 허용하는 단색 사용을 따라 전경색으로 칠한다. 구글 로고는 공식 4색이라
 * 어느 배경에서도 다시 칠하지 않는다.
 */
internal val AuthProvider.chipLogoTint: Color?
    @Composable get() =
        when (this) {
            AuthProvider.GOOGLE -> null
            AuthProvider.KAKAO -> ManyakTheme.colors.text
        }
