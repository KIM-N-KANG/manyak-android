package app.manyak.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.core.domain.story.CharacterGender
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

@Composable
internal fun GenderSelectField(
    gender: CharacterGender?,
    onGenderChange: (CharacterGender?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val options =
        listOf(
            null to stringResource(R.string.create_gender_random),
            CharacterGender.MALE to stringResource(R.string.create_gender_male),
            CharacterGender.FEMALE to stringResource(R.string.create_gender_female),
        )
    val selectedLabel = options.first { it.first == gender }.second

    Box(modifier = modifier.onSizeChanged { size -> anchorWidthPx = size.width }) {
        GenderSelectAnchor(
            label = selectedLabel,
            isPlaceholder = gender == null,
            expanded = expanded,
            onClick = { expanded = true },
        )
        if (expanded) {
            GenderSelectMenu(
                anchorWidthPx = anchorWidthPx,
                selected = gender,
                options = options,
                onDismiss = { expanded = false },
                onSelect = { value ->
                    expanded = false
                    onGenderChange(value)
                },
            )
        }
    }
}

/**
 * M3 `DropdownMenu`는 공간에 따라 위로 뒤집히므로, 항상 앵커 아래에 열리도록 위치를 직접 계산한다.
 * 흰 앵커와 메뉴의 경계를 구분하기 위해 디자인 시스템의 무그림자 원칙에서 예외로 둔다.
 */
@Composable
private fun GenderSelectMenu(
    anchorWidthPx: Int,
    selected: CharacterGender?,
    options: List<Pair<CharacterGender?, String>>,
    onDismiss: () -> Unit,
    onSelect: (CharacterGender?) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { ManyakTheme.spacing.inline.roundToPx() }
    val positionProvider =
        remember(gapPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(x = anchorBounds.left, y = anchorBounds.bottom + gapPx)
            }
        }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                Modifier
                    .width(with(density) { anchorWidthPx.toDp() })
                    .shadow(elevation = 1.dp, shape = ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                    .border(1.dp, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                    .clip(ManyakTheme.shapes.control)
                    .padding(ManyakTheme.spacing.compact),
        ) {
            options.forEach { (value, label) ->
                GenderMenuItem(
                    label = label,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun GenderMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ManyakTheme.shapes.menuItem)
                .background(
                    if (selected) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.surfaceRaised,
                ).clickable(onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.text,
        )
        if (selected) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = ManyakTheme.colors.text,
            )
        }
    }
}

@Composable
private fun GenderSelectAnchor(
    label: String,
    isPlaceholder: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ManyakTheme.sizes.input)
                .clip(ManyakTheme.shapes.control)
                .background(ManyakTheme.colors.surfaceRaised)
                .border(1.dp, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                .clickable(onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.controlHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = ManyakTheme.typography.bodyMedium,
            color = if (isPlaceholder) ManyakTheme.colors.textDisabled else ManyakTheme.colors.text,
        )
        Icon(
            modifier = Modifier.size(16.dp),
            painter =
                painterResource(
                    if (expanded) R.drawable.ic_angle_up else R.drawable.ic_angle_down,
                ),
            contentDescription = null,
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}
