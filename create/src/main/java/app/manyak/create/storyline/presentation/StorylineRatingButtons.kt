package app.manyak.create.storyline.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.manyak.create.entity.StorylineRating
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignsystemR

/** 활성 평가가 쓰는 채움·경계·아이콘 색 조합. 키워드 칩의 선택 문법과 같다. */
private data class RatingActiveColors(
    val background: Color,
    val border: Color,
    val content: Color,
)

/** 좋아요·별로예요 토글 쌍. 같은 평가를 다시 누르면 해제된다. */
@Composable
internal fun StorylineRatingButtons(
    rating: StorylineRating?,
    onToggle: (StorylineRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        StorylineRatingButton(
            active = rating == StorylineRating.GOOD,
            activeColors =
                RatingActiveColors(
                    background = ManyakTheme.colors.backgroundBrandSubtle,
                    border = ManyakTheme.colors.borderBrand,
                    content = ManyakTheme.colors.textBrand,
                ),
            iconRes = DesignsystemR.drawable.ic_thumb_up,
            contentDescription = stringResource(CreateR.string.create_storyline_rating_good),
            onClick = { onToggle(StorylineRating.GOOD) },
        )
        StorylineRatingButton(
            active = rating == StorylineRating.BAD,
            activeColors =
                RatingActiveColors(
                    background = ManyakTheme.colors.backgroundDangerSubtle,
                    border = ManyakTheme.colors.borderDanger,
                    content = ManyakTheme.colors.textDanger,
                ),
            iconRes = DesignsystemR.drawable.ic_thumb_down,
            contentDescription = stringResource(CreateR.string.create_storyline_rating_bad),
            onClick = { onToggle(StorylineRating.BAD) },
        )
    }
}

/** 키워드 칩과 같은 시각 문법 — 기본은 흰 배경 + 옅은 경계, 활성은 subtle 채움 + 경계·아이콘 색. */
@Composable
private fun StorylineRatingButton(
    active: Boolean,
    activeColors: RatingActiveColors,
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (active) activeColors.background else ManyakTheme.colors.surfaceRaised
    val borderColor = if (active) activeColors.border else ManyakTheme.colors.border
    val iconColor = if (active) activeColors.content else ManyakTheme.colors.text
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(background)
                .border(1.dp, borderColor, ManyakTheme.shapes.control)
                .toggleable(
                    value = active,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onValueChange = { onClick() },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconColor,
        )
    }
}
