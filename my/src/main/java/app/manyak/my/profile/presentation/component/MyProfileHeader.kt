package app.manyak.my.profile.presentation.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.user.UserProfile
import app.manyak.designsystem.component.SkeletonPlaceholder
import app.manyak.designsystem.component.rememberSkeletonPulseAlpha
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.my.presentation.component.chipLogoTint
import app.manyak.my.presentation.component.labelRes
import app.manyak.my.presentation.component.logoRes
import coil3.compose.AsyncImage
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.my.R as MyR

@Composable
internal fun ProfileHeader(
    profile: UserProfile?,
    onLinkAccount: (AuthProvider) -> Unit,
    onOpenEdit: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 영역 전체가 수정 진입이다. 안쪽 연동 버튼은 자기 클릭이 먼저 받아 그대로 동작한다.
    // 사방 여백을 클릭 안쪽에 둬 눌림이 메뉴 줄처럼 화면 폭 전체와 위아래 여백까지 번진다.
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    enabled = profile != null,
                    onClickLabel = stringResource(MyR.string.my_profile_edit_action),
                    role = Role.Button,
                    onClick = onOpenEdit,
                ).padding(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        ProfileAvatar(profile = profile)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            NicknameRow(nickname = profile?.nickname.orEmpty(), showsChevron = profile != null)
            if (isLoading && profile == null) {
                val alpha = rememberSkeletonPulseAlpha()
                Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
                    repeat(2) {
                        SkeletonPlaceholder(
                            alpha = alpha,
                            modifier =
                                Modifier.size(
                                    width = ManyakTheme.sizes.control * 2,
                                    height = ManyakTheme.sizes.tabIcon,
                                ),
                        )
                    }
                }
            }
            // 연동 목록이 비어 있으면 아직 실데이터가 아니다. 연동 버튼 둘만 뜨는 오해를 만들지 않는다.
            profile?.linkedProviders?.takeIf { it.isNotEmpty() }?.let { linked ->
                LinkedAccountRow(linked = linked, onLinkAccount = onLinkAccount)
            }
        }
    }
}

/** 웹과 같이 꺾쇠를 닉네임 바로 뒤에 붙인다. 긴 닉네임은 줄이고 꺾쇠는 남긴다. */
@Composable
private fun NicknameRow(
    nickname: String,
    showsChevron: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Text(
            text = nickname,
            modifier = Modifier.weight(1f, fill = false),
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showsChevron) {
            Icon(
                painter = painterResource(DesignsystemR.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                tint = ManyakTheme.colors.textSubtlest,
            )
        }
    }
}

/** 인라인 썸네일(base64)을 우선 쓰고, 없으면 원본 URL, 그것도 없으면 빈 원을 그린다. */
@Composable
private fun ProfileAvatar(
    profile: UserProfile?,
    modifier: Modifier = Modifier,
) {
    val thumbnail = rememberProfileThumbnail(profile?.profileThumbnailBase64)
    val avatarModifier =
        modifier
            .size(ProfileAvatarSize)
            .clip(ManyakTheme.shapes.pill)
    when {
        thumbnail != null ->
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = avatarModifier,
                contentScale = ContentScale.Crop,
            )

        profile?.profileImageUrl != null ->
            AsyncImage(
                model = profile.profileImageUrl,
                contentDescription = null,
                modifier = avatarModifier.background(ManyakTheme.colors.backgroundNeutral),
                contentScale = ContentScale.Crop,
            )

        else -> Box(modifier = avatarModifier.background(ManyakTheme.colors.backgroundNeutral))
    }
}

/**
 * 연동된 제공자는 실선 칩, 아직 아닌 제공자는 같은 모양의 점선 버튼이다. 둘 다 연동하면 버튼이 사라져
 * 칩만 남는다. 큰 글자에서 한 줄에 다 들어가지 않으면 줄을 바꾼다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedAccountRow(
    linked: List<AuthProvider>,
    onLinkAccount: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        linked.forEach { provider ->
            ProviderChip(
                logoRes = provider.logoRes,
                logoTint = provider.chipLogoTint,
                label = stringResource(provider.labelRes),
                modifier =
                    Modifier.border(
                        width = ProviderChipBorderWidth,
                        color = ManyakTheme.colors.border,
                        shape = ManyakTheme.shapes.pill,
                    ),
            )
        }
        AuthProvider.entries
            .filterNot { it in linked }
            .forEach { provider ->
                ProviderChip(
                    logoRes = provider.logoRes,
                    logoTint = provider.chipLogoTint,
                    label = stringResource(MyR.string.my_link_button, stringResource(provider.labelRes)),
                    modifier =
                        Modifier
                            .clip(ManyakTheme.shapes.pill)
                            .clickable { onLinkAccount(provider) }
                            .dashedPillBorder(
                                // 점선은 잉크가 절반만 닿아 실선 칩 옆에서 같은 색으로는 연하게 읽힌다.
                                color = ManyakTheme.colors.borderStrong,
                                width = ProviderChipBorderWidth,
                            ),
                )
            }
    }
}

@Composable
private fun ProviderChip(
    @DrawableRes logoRes: Int,
    logoTint: Color?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(ProviderChipHeight)
                .padding(horizontal = ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense),
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            modifier = Modifier.size(ProviderLogoSize),
            colorFilter = logoTint?.let(ColorFilter::tint),
        )
        Text(
            text = label,
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/**
 * 점선은 "아직 연동되지 않았다"를 색이 아니라 선의 모양으로 말한다. `border` 는 점선을 그리지 못해
 * 같은 알약 윤곽을 직접 그린다.
 */
private fun Modifier.dashedPillBorder(
    color: Color,
    width: Dp,
): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRoundRect(
            color = color,
            // 선은 경계 안쪽에 그린다. 절반을 밀지 않으면 바깥쪽 절반이 잘린다.
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(size.height / 2),
            style =
                Stroke(
                    width = stroke,
                    pathEffect =
                        PathEffect.dashPathEffect(
                            floatArrayOf(ProviderChipDashLength.toPx(), ProviderChipDashGap.toPx()),
                        ),
                ),
        )
    }

/** 웹 마이 화면의 아바타(56px)와 같은 크기다. */
private val ProfileAvatarSize = 56.dp

/** 연동 칩의 높이·로고 크기는 웹 배지(24px 높이, 12px 로고)와 맞춘다. */
private val ProviderChipHeight = 24.dp
private val ProviderLogoSize = 12.dp
private val ProviderChipBorderWidth = 1.dp
private val ProviderChipDashLength = 3.dp
private val ProviderChipDashGap = 3.dp
