package app.manyak.create.presentation.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.manyak.create.entity.StoryTagCategory
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.create.R as CreateR
import app.manyak.designsystem.R as DesignsystemR

@Composable
internal fun KeywordSectionLabel(
    text: String,
    required: Boolean,
    modifier: Modifier = Modifier,
) {
    val label =
        buildAnnotatedString {
            append(text)
            if (required) {
                append(" ")
                withStyle(SpanStyle(color = ManyakTheme.colors.textDanger)) { append("*") }
            }
        }
    Text(
        modifier = modifier,
        text = label,
        style = ManyakTheme.typography.labelLarge,
        color = ManyakTheme.colors.text,
    )
}

/** 다시 고를 수 없는 자리에서 보여 주는 키워드. 선택 상태와 같은 모양이되 누를 수 없다. */
@Composable
internal fun SelectedKeywordChip(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.backgroundBrandSubtle)
                .border(1.dp, ManyakTheme.colors.borderBrand, ManyakTheme.shapes.control)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textBrand,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AddTrigger(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ManyakTheme.shapes.control,
    @DrawableRes iconRes: Int = DesignsystemR.drawable.ic_add,
) {
    val contentColor = if (enabled) ManyakTheme.colors.text else ManyakTheme.colors.textDisabled
    Row(
        modifier =
            modifier
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(shape)
                .background(ManyakTheme.colors.backgroundNeutral)
                .border(1.dp, ManyakTheme.colors.border, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.inline),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = label,
            style = ManyakTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

internal val StoryTagCategory.labelRes: Int
    @StringRes
    get() =
        when (this) {
            StoryTagCategory.GENRE -> CreateR.string.create_tab_genre
            StoryTagCategory.PROTAGONIST -> CreateR.string.create_tab_protagonist
            StoryTagCategory.SUPPORTING_CHARACTER -> CreateR.string.create_tab_supporting_character
        }

/**
 * 새로 추가한 칸을 화면 가운데로 옮긴다. 웹의 부드러운 스크롤처럼 천천히 출발해 천천히 멈춘다 — 기본 스프링은
 * 첫 프레임에 크게 튀고 금방 끝나 끌려가는 느낌을 준다.
 *
 * @param fieldCenter 칸 가운데의 루트 기준 y
 * @param viewportCenter 스크롤 영역 가운데의 루트 기준 y
 */
internal suspend fun ScrollableState.scrollToCenter(
    fieldCenter: Float,
    viewportCenter: Float,
) {
    animateScrollBy(fieldCenter - viewportCenter, tween(SCROLL_TO_FIELD_MILLIS, easing = FastOutSlowInEasing))
}

private const val SCROLL_TO_FIELD_MILLIS = 400
