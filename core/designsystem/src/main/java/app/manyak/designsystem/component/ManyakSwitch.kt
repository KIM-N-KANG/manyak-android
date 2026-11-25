package app.manyak.designsystem.component

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 앱 색을 얹은 M3 스위치. 크기·모양은 M3 기본 그대로다.
 *
 * [onCheckedChange] 가 null 이면 표시만 맡는다 — 행 전체가 토글을 받는 자리(채팅 설정 시트)에서
 * 스위치에도 클릭을 달면 한 번의 탭이 두 번 토글되고 접근성 노드도 둘로 읽힌다.
 *
 * [enabled] 가 거짓이면 켜짐·꺼짐 위치는 유지한 채 비활성 채움·비활성 글자색으로 눌러 그린다 — 값은 살아 있지만
 * 지금은 손댈 수 없는 상태(기기 알림 꺼짐)를 위치와 색으로 함께 보여 준다.
 */
@Composable
fun ManyakSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = ManyakTheme.colors.textInverse,
                checkedTrackColor = ManyakTheme.colors.brand,
                uncheckedThumbColor = ManyakTheme.colors.textSubtlest,
                uncheckedTrackColor = ManyakTheme.colors.backgroundNeutral,
                uncheckedBorderColor = ManyakTheme.colors.borderStrong,
                disabledCheckedThumbColor = ManyakTheme.colors.textInverse,
                disabledCheckedTrackColor = ManyakTheme.colors.textDisabled,
                disabledUncheckedThumbColor = ManyakTheme.colors.textDisabled,
                disabledUncheckedTrackColor = ManyakTheme.colors.backgroundDisabled,
                disabledUncheckedBorderColor = ManyakTheme.colors.backgroundDisabled,
            ),
    )
}
