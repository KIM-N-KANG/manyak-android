package app.manyak.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.theme.insetForBorder
import coil3.compose.AsyncImage
import java.net.URI
import java.net.URISyntaxException

/**
 * 인물 이미지. 4:3 으로 두고 잘라내지 않는다.
 *
 * **불러오지 못하면 자리째로 사라진다** — placeholder 를 남기면 빈 상자가 읽는 흐름을 끊는다.
 * 허용 주소 검사를 그리기 직전에 한 번 더 하는 이유는 채팅 마커가 AI 가 만든 본문에서 오기
 * 때문이다.
 */
@Composable
fun CharacterImage(
    name: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    var failed by rememberSaveable(imageUrl) { mutableStateOf(false) }
    if (failed || !isAllowedCharacterImageUrl(imageUrl)) return

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(CHARACTER_IMAGE_ASPECT_RATIO)
                .clip(ManyakTheme.shapes.overlay)
                // 테두리는 선을 얹지 않고 바탕으로 그린다 — 표지 썸네일과 같은 이유다.
                .background(ManyakTheme.colors.border),
    ) {
        AsyncImage(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(ImageBorderWidth)
                    .clip(ManyakTheme.shapes.overlay.insetForBorder(ImageBorderWidth))
                    .background(ManyakTheme.colors.backgroundNeutral),
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
        )
    }
}

/**
 * 인물 이미지로 허용된 주소인지 본다.
 *
 * **주소를 그대로 믿지 않는다.** 채팅 마커는 AI 가 만든 본문에서 오므로, 임의 호스트를 그리면
 * 본문이 외부로 요청을 내보내는 통로가 된다.
 */
fun isAllowedCharacterImageUrl(imageUrl: String): Boolean =
    try {
        val uri = URI(imageUrl)
        uri.scheme == "https" &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.host in AllowedImageHosts &&
            uri.path != null &&
            AllowedImagePathPrefixes.any { uri.path.startsWith(it) && uri.path.length > it.length }
    } catch (_: URISyntaxException) {
        false
    }

/** 인물 이미지의 가로세로 비율. */
private const val CHARACTER_IMAGE_ASPECT_RATIO = 4f / 3f

private val ImageBorderWidth = 1.dp

private val AllowedImageHosts = setOf("cdn.manyak.app", "dev-cdn.manyak.app")

/** 사용자가 만든 스토리는 `generated`, 오리지널 스토리는 `originals` 아래에 인물 이미지가 올라간다. */
private val AllowedImagePathPrefixes = listOf("/characters/generated/", "/characters/originals/")
