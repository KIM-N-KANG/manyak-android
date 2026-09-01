package app.manyak.feature.my

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.component.ManyakProgressIndicator
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 마이 하위 화면들의 폼 조각. 초대·피드백 두 화면이 같은 버튼을 쓴다.
 *
 * `:core:ui` 로 올리지 않는다 — 아직 이 모듈 밖에 쓰는 곳이 없다.
 *
 * @param isDanger 되돌릴 수 없는 동작. 형태는 그대로 두고 배경만 위험 색으로 바꾼다.
 * @param isCompact 입력창 옆에 나란히 서는 버튼. 높이를 입력창과 같은 40dp 로 낮춘다 — 옆 칸보다
 *   크면 한 줄로 읽히지 않는다. 최소 터치 타깃 48dp 에 못 미치는 것은 칩과 같은 예외로 수용한다.
 */
@Composable
@Suppress("LongParameterList")
internal fun MyPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isCompact: Boolean = false,
    isDanger: Boolean = false,
    @DrawableRes iconRes: Int? = null,
) {
    Button(
        modifier =
            modifier.heightIn(
                min = if (isCompact) ManyakTheme.sizes.input else ManyakTheme.sizes.control,
            ),
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = ManyakTheme.shapes.control,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isDanger) ManyakTheme.colors.backgroundDangerBold else ManyakTheme.colors.brand,
                contentColor = ManyakTheme.colors.textInverse,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 진행 중에도 라벨 자리를 유지해 버튼 폭이 스피너 폭으로 줄지 않게 한다.
            ButtonContent(label = label, iconRes = iconRes, modifier = Modifier.alpha(if (isLoading) 0f else 1f))
            if (isLoading) {
                ManyakProgressIndicator(
                    modifier = Modifier.size(ManyakTheme.sizes.icon),
                    color = LocalContentColor.current,
                )
            }
        }
    }
}

/** 주 동작 옆에 나란히 서는 보조 버튼. 채움 없이 경계만 두어 무게를 낮춘다. */
@Composable
internal fun MyOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = null,
) {
    Button(
        modifier = modifier.heightIn(min = ManyakTheme.sizes.control),
        onClick = onClick,
        enabled = enabled,
        shape = ManyakTheme.shapes.control,
        border = BorderStroke(1.dp, ManyakTheme.colors.border),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = ManyakTheme.colors.surface,
                contentColor = ManyakTheme.colors.text,
                disabledContainerColor = ManyakTheme.colors.backgroundDisabled,
                disabledContentColor = ManyakTheme.colors.textDisabled,
            ),
    ) {
        ButtonContent(label = label, iconRes = iconRes)
    }
}

@Composable
private fun ButtonContent(
    label: String,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            )
        }
        Text(text = label, style = ManyakTheme.typography.labelLarge, maxLines = 1)
    }
}

/** 입력 항목의 라벨. [isRequired] 는 웹과 같이 별표 하나로만 드러낸다. */
@Composable
internal fun MyFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
) {
    Row(modifier = modifier) {
        Text(text = text, style = ManyakTheme.typography.labelLarge, color = ManyakTheme.colors.text)
        if (isRequired) {
            Text(
                text = stringResource(R.string.form_required_mark),
                style = ManyakTheme.typography.labelLarge,
                color = ManyakTheme.colors.textDanger,
            )
        }
    }
}

/** 입력 아래에 붙는 오류·보조 문구. */
@Composable
internal fun MyFieldMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Text(
        modifier = modifier,
        text = text,
        style = ManyakTheme.typography.bodyMedium,
        color = if (isError) ManyakTheme.colors.textDanger else ManyakTheme.colors.textSubtle,
    )
}
