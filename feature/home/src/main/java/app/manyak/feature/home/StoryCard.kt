package app.manyak.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.manyak.core.domain.story.StorySummary
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import coil3.compose.AsyncImage
import java.text.NumberFormat

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
        StoryThumbnail(thumbnailUrl = story.thumbnailUrl, turnCount = story.turnCount)
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

@Composable
private fun StoryThumbnail(
    thumbnailUrl: String?,
    turnCount: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(THUMBNAIL_ASPECT_RATIO)
                .clip(ManyakTheme.shapes.thumbnail)
                .background(ManyakTheme.colors.backgroundNeutral),
    ) {
        if (thumbnailUrl == null) {
            Icon(
                modifier = Modifier.align(Alignment.Center).size(ThumbnailPlaceholderSize),
                painter = painterResource(R.drawable.ic_image),
                contentDescription = stringResource(R.string.home_thumbnail_placeholder),
                tint = ManyakTheme.colors.textSubtlest,
            )
        } else {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = thumbnailUrl,
                // 표지는 제목·제작자가 이미 말하는 것을 되풀이하므로 낭독 대상이 아니다.
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )
        }
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
        TurnCountBadge(
            modifier = Modifier.align(Alignment.BottomEnd).padding(ManyakTheme.spacing.compact),
            turnCount = turnCount,
        )
    }
}

/** 누적 턴 수. 표지 위에 놓이므로 색은 테마가 아니라 표지 대비로 정한다. */
@Composable
private fun TurnCountBadge(
    turnCount: Long,
    modifier: Modifier = Modifier,
) {
    val formatted = remember(turnCount) { NumberFormat.getIntegerInstance().format(turnCount) }
    val description = stringResource(R.string.home_turn_count_description, formatted)

    Row(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.pill)
                .background(OverlayScrim)
                .padding(
                    horizontal = ManyakTheme.spacing.compact,
                    vertical = ManyakTheme.spacing.hairline,
                )
                // 숫자만 읽히면 무엇의 수인지 알 수 없어 배지 전체를 한 문장으로 읽힌다.
                .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Icon(
            modifier = Modifier.size(BadgeIconSize),
            painter = painterResource(R.drawable.ic_nav_chat_outline),
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            text = formatted,
            style = ManyakTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/** 표지 비율. 3:4 세로형이다. 골격도 같은 비율을 써야 목록이 도착할 때 자리가 튀지 않는다. */
internal const val THUMBNAIL_ASPECT_RATIO = 3f / 4f

private const val ORIGINAL_TAG_ASPECT_RATIO = 600f / 216f

/**
 * ORIGINAL 태그의 폭.
 *
 * 태그 도안의 좌상단 라운드는 폭의 92/600 이다. 썸네일 모서리(12dp)와 곡률이 어긋나면 겹친 자리가
 * 어긋나 보이므로, 같은 12dp 가 나오는 폭(12 × 600 ÷ 92)을 역산해 고정했다.
 */
private val OriginalTagWidth = 78.dp

private val BadgeIconSize = 14.dp

private val ThumbnailPlaceholderSize = 32.dp

/**
 * 태그 도안이 쓰는 것과 같은 반투명 필드라 두 겹침 요소의 질감이 맞는다.
 *
 * 웹은 여기에 backdrop blur 를 더하지만 앱은 반투명만 쓴다 — 뒤 콘텐츠를 흐리는 수단이
 * `RenderEffect`(API 31+) 뿐이라 minSdk 24 의 전 기기에 걸리지 않고, 스크롤되는 그리드에서
 * 카드마다 레이어를 기록하는 비용도 크다.
 */
private val OverlayScrim = Color(0xFF111414).copy(alpha = 0.2f)
