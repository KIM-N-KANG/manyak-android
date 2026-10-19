package app.manyak.my.profile.presentation.component

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import app.manyak.designsystem.theme.ManyakTheme
import coil3.compose.AsyncImage

/**
 * 원형 프로필 이미지. 48px 인라인 썸네일을 먼저 깔고 원본이 오면 바꾼다. 마이 헤더보다 크게 그리는 곳에서
 * 쓴다. 썸네일만으로는 흐려 보이는 크기다.
 */
@Composable
internal fun ProfileImage(
    imageUrl: String?,
    thumbnailBase64: String?,
    modifier: Modifier = Modifier,
) {
    val thumbnail = rememberProfileThumbnail(thumbnailBase64)
    val placeholder = remember(thumbnail) { thumbnail?.let(::BitmapPainter) }
    val imageModifier =
        modifier
            .clip(ManyakTheme.shapes.pill)
            .background(ManyakTheme.colors.backgroundNeutral)
    if (imageUrl == null) {
        Box(modifier = imageModifier)
        return
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        modifier = imageModifier,
        placeholder = placeholder,
        contentScale = ContentScale.Crop,
    )
}

@Composable
internal fun rememberProfileThumbnail(base64: String?): ImageBitmap? =
    remember(base64) {
        base64?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
