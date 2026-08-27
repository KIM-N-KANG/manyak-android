package app.manyak.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme
import coil3.compose.AsyncImage
import java.text.NumberFormat

/**
 * 목록 카드의 3:4 표지. 원본이 없으면 placeholder 아이콘을 그리고, 우하단에 누적 턴 수 뱃지를
 * 얹는다. 카드 종류별 표시(예: 홈의 ORIGINAL 태그)는 [overlay] 로 표지 위에 더한다.
 *
 * 화면 폭을 꽉 채우는 자리에서는 [shape] 를 각지게 바꾼다 — 가장자리에 닿은 둥근 모서리는
 * 화면이 잘린 것처럼 보인다.
 *
 * 목록 카드처럼 표지가 배경 위에 떠 있는 자리에서는 [showBorder] 로 테두리를 둘러 밝은 표지의
 * 가장자리가 배경에 묻히지 않게 한다.
 */
@Composable
fun StoryThumbnail(
    thumbnailUrl: String?,
    turnCount: Long,
    modifier: Modifier = Modifier,
    badgeScale: StoryBadgeScale = StoryBadgeScale.Compact,
    shape: Shape = ManyakTheme.shapes.thumbnail,
    showBorder: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    // border 는 자기 안쪽을 먼저 그린 뒤 선을 얹으므로 표지 위로 올라온다. clip 보다 앞에 둬야
    // 선의 바깥쪽이 잘리지 않는다.
    val borderModifier =
        if (showBorder) {
            Modifier.border(ThumbnailBorderWidth, ManyakTheme.colors.border, shape)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO)
                .then(borderModifier)
                .clip(shape)
                .background(ManyakTheme.colors.backgroundNeutral),
    ) {
        // 표지는 선 두께만큼 안으로 들인다. 표지와 선의 경계가 같은 픽셀에 겹치면 두 안티에일리어싱이
        // 합성돼 곡률에서만 진한 띠가 생기고, 그 띠가 직선부의 선보다 도드라진다.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .then(if (showBorder) Modifier.padding(ThumbnailBorderWidth) else Modifier)
                    .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl == null) {
                Icon(
                    modifier = Modifier.size(ThumbnailPlaceholderSize),
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = stringResource(R.string.story_thumbnail_placeholder),
                    tint = ManyakTheme.colors.textSubtlest,
                )
            } else {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = thumbnailUrl,
                    // 표지는 카드의 텍스트 줄이 이미 말하는 것을 되풀이하므로 낭독 대상이 아니다.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                )
            }
        }
        overlay()
        TurnCountBadge(
            modifier = Modifier.align(Alignment.BottomEnd).padding(ManyakTheme.spacing.compact),
            turnCount = turnCount,
            scale = badgeScale,
        )
    }
}

/** 누적 턴 수. 표지 위에 놓이므로 색은 테마가 아니라 표지 대비로 정한다. */
@Composable
private fun TurnCountBadge(
    turnCount: Long,
    scale: StoryBadgeScale,
    modifier: Modifier = Modifier,
) {
    val formatted = remember(turnCount) { NumberFormat.getIntegerInstance().format(turnCount) }
    val description = stringResource(R.string.story_turn_count_description, formatted)

    Row(
        modifier =
            modifier
                .clip(ManyakTheme.shapes.pill)
                .background(StoryOverlayScrim)
                .padding(
                    horizontal = scale.horizontalPadding,
                    vertical = scale.verticalPadding,
                )
                // 숫자만 읽히면 무엇의 수인지 알 수 없어 배지 전체를 한 문장으로 읽힌다.
                .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Icon(
            modifier = Modifier.size(if (scale == StoryBadgeScale.Compact) BadgeIconSize else LargeBadgeIconSize),
            painter = painterResource(R.drawable.ic_dialog),
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            text = formatted,
            style = scale.textStyle,
            color = Color.White,
        )
    }
}

/** 표지 비율. 3:4 세로형이다. 골격도 같은 비율을 써야 목록이 도착할 때 자리가 튀지 않는다. */
const val STORY_THUMBNAIL_ASPECT_RATIO = 3f / 4f

private val BadgeIconSize = 14.dp

/** 상세 히어로의 뱃지. 글자가 한 단계 커진 만큼 아이콘도 함께 키운다. */
private val LargeBadgeIconSize = 16.dp

private val ThumbnailPlaceholderSize = 32.dp

private val ThumbnailBorderWidth = 1.dp

/**
 * 표지 위 겹침 요소(턴 수 뱃지와 [overlay] 의 카드별 요소)가 함께 쓰는 반투명 필드.
 * 태그 도안도 같은 필드를 그려 겹침 요소끼리 질감이 맞는다.
 * 값을 바꾸면 `ic_story_original_tag` 의 `fillAlpha` 도 같이 바꿔야 한다.
 *
 * 웹은 여기에 backdrop blur 를 더하지만 앱은 반투명만 쓴다 — 뒤 콘텐츠를 흐리는 수단이
 * `RenderEffect`(API 31+) 뿐이라 minSdk 24 의 전 기기에 걸리지 않고, 스크롤되는 그리드에서
 * 카드마다 레이어를 기록하는 비용도 크다. 블러가 표지의 잔무늬를 지워 주지 않는 만큼 웹의 20% 보다
 * 진하게 가려야 흰 글자가 읽힌다.
 */
val StoryOverlayScrim = Color(0xFF111414).copy(alpha = OVERLAY_SCRIM_ALPHA)

private const val OVERLAY_SCRIM_ALPHA = 0.7f
