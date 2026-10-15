package app.manyak.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import app.manyak.designsystem.text.formatCompactCount
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.theme.insetForBorder
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.manyak.designsystem.R as DesignsystemR

/**
 * 목록 카드의 3:4 표지에 좋아요 수·누적 턴 수 뱃지를 얹은 것. 카드 종류별 표시(예: 홈의
 * ORIGINAL 태그)는 [overlay] 로 표지 위에 더한다.
 *
 * 뱃지를 얹을 수 없는 자리 — 채팅 목록 카드처럼 표지가 작거나, 턴 수를 카드의 다른 자리가 이미
 * 말하는 곳 — 는 [StoryCover] 를 직접 쓴다.
 */
@Composable
fun StoryThumbnail(
    thumbnailUrl: String?,
    likeCount: Long,
    turnCount: Long,
    modifier: Modifier = Modifier,
    badgeScale: StoryBadgeScale = StoryBadgeScale.Compact,
    shape: Shape = ManyakTheme.shapes.card,
    showBorder: Boolean = false,
    overlay: @Composable CoverOverlayScope.() -> Unit = {},
) {
    StoryCover(
        thumbnailUrl = thumbnailUrl,
        modifier = modifier,
        shape = shape,
        showBorder = showBorder,
    ) {
        overlay()
        // 뱃지 줄의 RowScope 안에서는 표지 자리의 receiver 를 암묵적으로 쓸 수 없어 미리 꺼낸다.
        val coverBackdrop = backdrop
        // 제작 탭 카드처럼 표지가 좁은 자리에서 큰 수 둘이 한 줄에 들지 않으면 뱃지 단위로 줄을 바꾼다.
        FlowRow(
            modifier = Modifier.align(Alignment.BottomEnd).padding(ManyakTheme.spacing.compact),
            horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
        ) {
            CountBadge(
                iconRes = DesignsystemR.drawable.ic_heart_outline,
                count = likeCount,
                descriptionRes = DesignsystemR.string.story_like_count_description,
                scale = badgeScale,
                backdrop = coverBackdrop,
            )
            CountBadge(
                iconRes = DesignsystemR.drawable.ic_dialog,
                count = turnCount,
                descriptionRes = DesignsystemR.string.story_turn_count_description,
                scale = badgeScale,
                backdrop = coverBackdrop,
            )
        }
    }
}

/**
 * 3:4 표지 본체. 원본이 없으면 placeholder 아이콘을 그린다. 표지 위에 얹는 것은 전부 [overlay] 가
 * 맡으므로 이 컴포넌트 자체는 무엇이 위에 오는지 알지 않는다. [overlay] 는 [CoverOverlayScope.backdrop]
 * 으로 뒤의 표지를 흐린 바탕을 깔 수 있다.
 *
 * 화면 폭을 꽉 채우는 자리에서는 [shape] 를 각지게 바꾼다 — 가장자리에 닿은 둥근 모서리는
 * 화면이 잘린 것처럼 보인다.
 *
 * 목록 카드처럼 표지가 배경 위에 떠 있는 자리에서는 [showBorder] 로 테두리를 둘러 밝은 표지의
 * 가장자리가 배경에 묻히지 않게 한다.
 */
@Composable
fun StoryCover(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = ManyakTheme.shapes.card,
    showBorder: Boolean = false,
    overlay: @Composable CoverOverlayScope.() -> Unit = {},
) {
    // 표지는 선 두께만큼 안으로 들이고 **반지름도 그만큼 줄여** 테두리와 동심원으로 맞춘다.
    // 반지름을 그대로 두면 곡률에서만 표지가 선 쪽으로 파고들어 그 부분이 진해 보인다.
    val coverModifier =
        if (showBorder) {
            Modifier
                .padding(ThumbnailBorderWidth)
                .clip(shape.insetForBorder(ThumbnailBorderWidth))
        } else {
            Modifier.clip(shape)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(STORY_THUMBNAIL_ASPECT_RATIO)
                .clip(shape)
                .background(
                    // 테두리는 선을 얹지 않고 **바탕으로 그린다**. 선을 얹으면 선의 안티에일리어싱과
                    // 표지의 안티에일리어싱이 같은 픽셀에서 합성돼 곡률만 진해진다.
                    if (showBorder) ManyakTheme.colors.border else ManyakTheme.colors.backgroundNeutral,
                ),
    ) {
        val backdrop = rememberCoverBackdrop()
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .then(coverModifier),
            contentAlignment = Alignment.Center,
        ) {
            // 흐림 원천은 표지 그림만 담는다. 겹침 요소까지 담으면 뱃지가 자기 자신을 흐려 깐다.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .coverBackdropSource(backdrop)
                        .background(ManyakTheme.colors.backgroundNeutral),
                contentAlignment = Alignment.Center,
            ) {
                CoverPicture(thumbnailUrl = thumbnailUrl)
            }
            // 겹침 요소도 테두리 안쪽에 둔다. 바깥에 두면 표지 위에 얹히면서 테두리까지 덮는다.
            val overlayScope = remember(this, backdrop) { CoverOverlayScopeImpl(this, backdrop) }
            overlayScope.overlay()
        }
    }
}

/** 표지 그림. 주소가 없으면 기본 심벌을 그린다. */
@Composable
private fun CoverPicture(thumbnailUrl: String?) {
    if (thumbnailUrl == null) {
        Icon(
            modifier = Modifier.size(ThumbnailPlaceholderSize),
            painter = painterResource(DesignsystemR.drawable.ic_manyak_symbol),
            contentDescription = stringResource(DesignsystemR.string.story_thumbnail_placeholder),
            tint = ManyakTheme.colors.textDisabled,
        )
    } else {
        val context = LocalPlatformContext.current
        // 받는 동안의 빈 바탕에서 그림이 한 프레임에 튀어나오지 않게 서서히 드러낸다.
        // 메모리 캐시에서 바로 나오는 표지는 Coil 이 페이드 없이 그린다.
        val request =
            remember(context, thumbnailUrl) {
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build()
            }
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = request,
            // 표지는 카드의 텍스트 줄이 이미 말하는 것을 되풀이하므로 낭독 대상이 아니다.
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * 표지 위 지표 뱃지 하나. 좋아요 수와 누적 턴 수가 같은 모양을 쓰므로 아이콘과 문구만 갈린다.
 * 표지 위에 놓이므로 색은 테마가 아니라 표지 대비로 정한다. 목록 카드와 상세 히어로 모두 웹 목록 카드처럼
 * 뒤의 표지를 흐려 깐다. 웹 상세는 흐림 없는 진한 바탕이지만 앱은 두 자리를 같은 결로 맞췄다.
 */
@Composable
private fun CountBadge(
    @DrawableRes iconRes: Int,
    count: Long,
    @StringRes descriptionRes: Int,
    scale: StoryBadgeScale,
    backdrop: CoverBackdrop?,
    modifier: Modifier = Modifier,
) {
    val formatted = remember(count) { formatCompactCount(count) }
    val description = stringResource(descriptionRes, formatted)

    Row(
        modifier =
            modifier
                .coverBackdrop(backdrop, ManyakTheme.shapes.pill)
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
            painter = painterResource(iconRes),
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
 * 뒤 그림을 흐릴 수 없는 API 31 미만에서 표지 위 겹침 요소가 흐림 대신 까는 필드([coverBackdrop]).
 * 흐림이 표지의 잔무늬를 지워 주지 않으므로 웹의 20% 그대로는 흰 글자가 읽히지 않아 70% 로 가린다.
 */
internal val StoryOverlayScrim = Color(0xFF111414).copy(alpha = OVERLAY_SCRIM_ALPHA)

private const val OVERLAY_SCRIM_ALPHA = 0.7f
