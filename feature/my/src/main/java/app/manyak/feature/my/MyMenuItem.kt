package app.manyak.feature.my

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.common.presentation.credit.LocalCreditPolicy
import app.manyak.common.presentation.credit.creditAmountText
import app.manyak.core.ui.R
import app.manyak.core.ui.credit.creditAmountAlpha
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 마이의 메뉴 줄. [onClick] 이 없으면 값만 보여 주는 행이다 — 버전처럼 열 곳이 없는 항목이 여기 해당한다.
 *
 * [subLabel] 은 라벨 아래에 강조색으로 붙어 라벨과 한 문장으로 이어 읽힌다. 아직 확정되지 않은 값을
 * 담고 있으면 [subLabelPending] 으로 알려 맥박을 준다.
 */
@Composable
@Suppress("LongParameterList")
internal fun MyMenuItem(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = ManyakTheme.colors.text,
    subLabel: String? = null,
    subLabelPending: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable =
        if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onClick)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.control)
                .then(clickable)
                .padding(
                    horizontal = ManyakTheme.spacing.gutter,
                    vertical = ManyakTheme.spacing.compact,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(MenuIconSize),
            tint = contentColor,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(labelRes),
                style = ManyakTheme.typography.bodyLarge,
                color = contentColor,
            )
            subLabel?.let {
                Text(
                    modifier = Modifier.alpha(creditAmountAlpha(subLabelPending)),
                    text = it,
                    style = ManyakTheme.typography.labelSmall,
                    color = ManyakTheme.colors.textBrand,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 목적지 이동·바깥 문서 열림을 알리는 오른쪽 끝 표시. */
@Composable
internal fun MenuTrailingIcon(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier.size(ManyakTheme.sizes.iconSmall),
        tint = ManyakTheme.colors.textSubtlest,
    )
}

/**
 * 친구 초대로 가는 줄. 마이와 이프 충전의 무료 충전 탭이 같은 줄을 써야 해서 한 곳에 둔다.
 */
@Composable
internal fun InviteMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inviteReward = LocalCreditPolicy.current?.inviteReward
    MyMenuItem(
        iconRes = R.drawable.ic_people,
        labelRes = R.string.my_invite,
        onClick = onClick,
        modifier = modifier,
        subLabel = stringResource(R.string.my_invite_reward, creditAmountText(inviteReward)),
        subLabelPending = inviteReward == null,
        trailing = { MenuTrailingIcon(iconRes = R.drawable.ic_chevron_right) },
    )
}

/** 메뉴 항목의 왼쪽 아이콘. 라벨 옆이지만 목록의 주된 시각 요소라 [ManyakTheme.sizes.icon]보다 크다. */
private val MenuIconSize = 24.dp
