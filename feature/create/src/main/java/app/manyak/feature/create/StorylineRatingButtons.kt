package app.manyak.feature.create

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

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
            activeColor = ManyakTheme.colors.brand,
            iconRes = R.drawable.ic_thumb_up,
            contentDescription = stringResource(R.string.create_storyline_rating_good),
            onClick = { onToggle(StorylineRating.GOOD) },
        )
        StorylineRatingButton(
            active = rating == StorylineRating.BAD,
            activeColor = ManyakTheme.colors.textDanger,
            iconRes = R.drawable.ic_thumb_down,
            contentDescription = stringResource(R.string.create_storyline_rating_bad),
            onClick = { onToggle(StorylineRating.BAD) },
        )
    }
}

@Composable
private fun StorylineRatingButton(
    active: Boolean,
    activeColor: Color,
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (active) activeColor else ManyakTheme.colors.border
    val iconColor = if (active) activeColor else ManyakTheme.colors.text
    Box(
        modifier =
            modifier
                .size(ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .border(1.dp, borderColor, ManyakTheme.shapes.control)
                .toggleable(
                    value = active,
                    role = Role.Button,
                    onValueChange = { onClick() },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconColor,
        )
    }
}
