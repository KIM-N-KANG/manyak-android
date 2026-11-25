package app.manyak.home.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.component.StoryBadgeScale
import app.manyak.designsystem.component.StoryGenreBadges
import app.manyak.designsystem.component.StoryThumbnail
import app.manyak.designsystem.component.originalTagBackdrop
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR
import app.manyak.home.R as HomeR

/**
 * 홈 섹션 카드. 표지 위에 오리지널 태그와 턴 수 뱃지를, 아래에 제목 한 줄, 한 줄 소개 두 줄, 장르 한 줄을
 * 크기와 굵기, 색을 한 단계씩 낮춰 쌓는다. 작성자는 상세에서 보인다.
 *
 * 소개는 비어 있어도 두 줄 자리를 지켜 옆 카드와 장르 줄의 높이가 맞는다. 고정 높이 대신 최소 줄 수로
 * 지켜 시스템 글자 크기를 키워도 잘리지 않는다.
 */
@Composable
internal fun StoryCard(
    story: StorySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // 카드 전체가 상세로 가는 링크다. 카드를 둥글게 클립하지 않는다. 맨 아래 줄이 아래 모서리 라운드에
        // 깎이고, 표지 곡률로 깎으면 표지 테두리 위에 클립 경계가 겹친다. 눌림 리플이 사각형으로 도는 편을 받아들인다.
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        StoryThumbnail(
            thumbnailUrl = story.thumbnailUrl,
            likeCount = null,
            turnCount = story.turnCount,
            showBorder = true,
        ) {
            if (story.isOriginal) {
                Image(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .width(OriginalTagWidth)
                            .aspectRatio(ORIGINAL_TAG_ASPECT_RATIO)
                            .originalTagBackdrop(backdrop),
                    painter = painterResource(DesignsystemR.drawable.ic_story_original_tag),
                    contentDescription = stringResource(HomeR.string.home_original_tag),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = ManyakTheme.spacing.inline)) {
            Text(
                text = story.title,
                style = ManyakTheme.typography.bodyLargeStrong.copy(lineBreak = LetterLineBreak),
                color = ManyakTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.padding(top = ManyakTheme.spacing.hairline),
                text = story.oneLineIntro,
                style = ManyakTheme.typography.bodyMedium.copy(lineBreak = LetterLineBreak),
                color = ManyakTheme.colors.textSubtle,
                minLines = INTRO_LINES,
                maxLines = INTRO_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (story.genres.isNotEmpty()) {
                StoryGenreBadges(
                    genres = story.genres,
                    scale = StoryBadgeScale.Compact,
                    modifier = Modifier.fillMaxWidth().padding(top = ManyakTheme.spacing.compact),
                )
            }
        }
    }
}

/**
 * 홈 카드의 제목과 소개는 어절이 아니라 글자 단위로 끊는다. 카드가 좁아 어절 단위로 끊으면 말줄임 앞에 빈 자리가
 * 크게 남는다. 줄 길이를 고르게 맞추는 배치도 앞 줄을 일찍 끊어 같은 빈 자리를 만들어 쓰지 않는다.
 */
private val LetterLineBreak = LineBreak.Simple

private const val INTRO_LINES = 2

private const val ORIGINAL_TAG_ASPECT_RATIO = 600f / 216f

/**
 * ORIGINAL 태그의 폭.
 *
 * 태그 도안의 좌상단 라운드는 폭의 92/600 이다. 썸네일 모서리(12dp)와 곡률이 어긋나면 겹친 자리가
 * 어긋나 보이므로, 같은 12dp 가 나오는 폭(12 × 600 ÷ 92)을 역산해 고정했다.
 */
private val OriginalTagWidth = 78.dp
