package app.manyak.feature.my

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.auth.AuthProvider
import app.manyak.core.domain.user.UserProfile
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import coil3.compose.AsyncImage

@Composable
internal fun ProfileHeader(
    profile: UserProfile?,
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
            profile?.linkedProviders?.takeIf { it.isNotEmpty() }?.let { providers ->
                Row(horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline)) {
                    providers.forEach { provider -> LinkedProviderBadge(provider = provider) }
                }
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

@Composable
private fun LinkedProviderBadge(
    provider: AuthProvider,
    modifier: Modifier = Modifier,
) {
    val (logoRes, labelRes) =
        when (provider) {
            AuthProvider.GOOGLE -> R.drawable.ic_logo_google to R.string.my_provider_google
            AuthProvider.KAKAO -> R.drawable.ic_logo_kakao to R.string.my_provider_kakao
        }
    Row(
        modifier =
            modifier
                .border(
                    width = 1.dp,
                    color = ManyakTheme.colors.border,
                    shape = ManyakTheme.shapes.pill,
                ).padding(
                    horizontal = ManyakTheme.spacing.compact,
                    vertical = ManyakTheme.spacing.inline,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
        )
        Text(
            text = stringResource(labelRes),
            style = ManyakTheme.typography.labelSmall,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 웹 마이 화면의 아바타(56px)와 같은 크기다. */
private val ProfileAvatarSize = 56.dp
