package app.manyak.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 장르 뱃지 하나. 목록 카드와 스토리 상세가 함께 쓴다.
 *
 * 배치는 두 곳이 다르다 — 카드는 폭에 맞춰 `+N` 으로 접고, 상세는 줄바꿈으로 모두 보인다. 공유하는
 * 것은 이 알약 하나뿐이라 배치는 각 화면이 소유한다.
 */
@Composable
fun StoryGenreBadge(
    text: String,
    modifier: Modifier = Modifier,
    scale: StoryBadgeScale = StoryBadgeScale.Compact,
) {
    Text(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.pill)
                .background(ManyakTheme.colors.backgroundNeutral)
                .padding(
                    horizontal = scale.horizontalPadding,
                    vertical = scale.verticalPadding,
                ),
        text = text,
        style = scale.textStyle,
        color = ManyakTheme.colors.textSubtle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
