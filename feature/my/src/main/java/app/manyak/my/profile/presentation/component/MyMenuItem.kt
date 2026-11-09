package app.manyak.my.profile.presentation.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import app.manyak.common.presentation.credit.LocalCreditPolicy
import app.manyak.common.presentation.credit.creditAmountText
import app.manyak.designsystem.credit.creditAmountAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.presentation.component.labelRes
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.my.R as MyR

/**
 * 마이의 메뉴 줄. [onClick] 이 없으면 값만 보여 주는 행이다 — 버전처럼 열 곳이 없는 항목이 여기 해당한다.
 *
 * [subLabel] 은 라벨 아래에 강조색으로 붙어 라벨과 한 문장으로 이어 읽힌다. 아직 확정되지 않은 값을
 * 담고 있으면 [subLabelPending] 으로 알려 맥박을 준다. [labelBadge] 는 라벨 오른쪽에 붙는 주의색 배지다 —
 * 공지사항의 "새 공지" 처럼 들어가 보라는 신호에 쓴다.
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
    labelBadge: String? = null,
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
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.component),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            tint = contentColor,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = ManyakTheme.typography.bodyLarge,
                    color = contentColor,
                )
                labelBadge?.let { DangerBadge(text = it) }
            }
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

/** 연한 빨간 바탕의 작은 배지. 색만으로 말하지 않도록 글자가 들어간다. */
@Composable
private fun DangerBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .background(ManyakTheme.colors.backgroundDangerSubtle, ManyakTheme.shapes.pill)
                .padding(horizontal = ManyakTheme.spacing.compact, vertical = ManyakTheme.spacing.hairline),
        text = text,
        style = ManyakTheme.typography.bodySmall,
        color = ManyakTheme.colors.textDanger,
    )
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
        iconRes = DesignsystemR.drawable.ic_people,
        labelRes = MyR.string.my_invite,
        onClick = onClick,
        modifier = modifier,
        subLabel = stringResource(MyR.string.my_invite_reward, creditAmountText(inviteReward)),
        subLabelPending = inviteReward == null,
        trailing = { MenuTrailingIcon(iconRes = DesignsystemR.drawable.ic_chevron_right) },
    )
}
