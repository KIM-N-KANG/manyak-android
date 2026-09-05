package app.manyak.feature.my

import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.auth.AuthProvider
import app.manyak.common.entity.user.UserProfile
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import coil3.compose.AsyncImage

@Composable
internal fun ProfileHeader(
    profile: UserProfile?,
    onLinkAccount: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(ManyakTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.gutter),
    ) {
        ProfileAvatar(profile = profile)
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
            Text(
                text = profile?.nickname.orEmpty(),
                style = ManyakTheme.typography.titleMediumStrong,
                color = ManyakTheme.colors.text,
            )
            // 연동 목록이 비어 있으면 아직 실데이터가 아니다. 연동 버튼 둘만 뜨는 오해를 만들지 않는다.
            profile?.linkedProviders?.takeIf { it.isNotEmpty() }?.let { linked ->
                LinkedAccountRow(linked = linked, onLinkAccount = onLinkAccount)
            }
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

@Composable
private fun rememberProfileThumbnail(base64: String?): ImageBitmap? =
    remember(base64) {
        base64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
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
                    label = stringResource(R.string.my_link_button, stringResource(provider.labelRes)),
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
