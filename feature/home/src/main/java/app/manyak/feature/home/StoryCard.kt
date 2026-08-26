package app.manyak.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.core.ui.component.StoryThumbnail
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 오리지널 스토리 카드.
 *
 * 제목과 제작자가 **1줄 고정**인 것이 이 카드의 규칙이다. 그래서 제목 길이와 무관하게 카드 높이가
 * 같고, 같은 행에 놓인 카드들의 제작자 줄이 같은 높이에 온다. 텍스트 영역에 고정 높이를 두면
 * 시스템 글자 크기를 키웠을 때 잘리므로 높이를 지정하지 않는다.
 */
@Composable
internal fun StoryCard(
    story: StorySummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        StoryThumbnail(thumbnailUrl = story.thumbnailUrl, turnCount = story.turnCount) {
            // 스크롤로 섹션 제목이 밀려 나가도 공식 스토리임이 카드 자체로 드러나게 하는 표시다.
            Image(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .width(OriginalTagWidth)
                        .aspectRatio(ORIGINAL_TAG_ASPECT_RATIO),
                painter = painterResource(R.drawable.ic_story_original_tag),
                contentDescription = stringResource(R.string.home_original_tag),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline)) {
            Text(
                text = story.title,
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 공식 계정이라 "마냑" 이 들어오지만, 작성자가 없는 스토리는 줄 자체를 그리지 않는다.
            story.authorNickname?.let { nickname ->
                Text(
                    text = nickname,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val ORIGINAL_TAG_ASPECT_RATIO = 600f / 216f

/**
 * ORIGINAL 태그의 폭.
 *
 * 태그 도안의 좌상단 라운드는 폭의 92/600 이다. 썸네일 모서리(12dp)와 곡률이 어긋나면 겹친 자리가
 * 어긋나 보이므로, 같은 12dp 가 나오는 폭(12 × 600 ÷ 92)을 역산해 고정했다.
 */
private val OriginalTagWidth = 78.dp
