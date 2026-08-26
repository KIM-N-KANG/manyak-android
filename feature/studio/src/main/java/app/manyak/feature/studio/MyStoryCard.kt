package app.manyak.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.component.StoryThumbnail
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 내가 만든 스토리 카드. 홈 카드와 표지·턴 수 뱃지는 같고, 텍스트 영역이 제작자 대신
 * 한 줄 소개와 장르 뱃지를 그린다. ORIGINAL 태그는 공식 스토리 표시라 붙이지 않는다.
 *
 * 제목·한 줄 소개·장르 뱃지가 모두 **1줄 고정**인 것이 이 카드의 규칙이다. 그래서 텍스트 길이와
 * 무관하게 카드 높이가 같다. 텍스트 영역에 고정 높이를 두면 시스템 글자 크기를 키웠을 때
 * 잘리므로 높이를 지정하지 않는다.
 */
@Composable
internal fun MyStoryCard(
    story: StorySummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        StoryThumbnail(thumbnailUrl = story.thumbnailUrl, turnCount = story.turnCount)
        Column(verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline)) {
            Text(
                text = story.title,
                style = ManyakTheme.typography.bodyLarge,
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 서버가 없는 소개를 빈 문자열로 주므로, 비면 줄 자체를 그리지 않는다.
            if (story.oneLineIntro.isNotBlank()) {
                Text(
                    text = story.oneLineIntro,
                    style = ManyakTheme.typography.bodyMedium,
                    color = ManyakTheme.colors.textSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (story.genres.isNotEmpty()) {
                StoryGenreBadges(
                    genres = story.genres,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // 텍스트 줄 간격(hairline)보다 뱃지는 조금 더 띄워야 다른 성질의 줄로 읽힌다.
                            .padding(top = ManyakTheme.spacing.inline),
                )
            }
        }
    }
}
