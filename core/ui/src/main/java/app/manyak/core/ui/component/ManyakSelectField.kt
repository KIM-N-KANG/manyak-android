package app.manyak.core.ui.component

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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 목록에서 하나를 고르는 셀렉트. 앵커를 누르면 바로 아래에 메뉴가 열린다.
 *
 * 펼침 상태는 이 컴포넌트가 든다 — 밖에서 알 필요가 없는 표현 상태이고, 호출부마다 상태를 두면
 * 열고 닫는 규칙이 화면마다 갈린다.
 */
@Composable
fun <T> ManyakSelectField(
    options: List<ManyakSelectOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
    onClickLabel: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    val selectedLabel = options.firstOrNull { option -> option.value == selected }?.label.orEmpty()

    Box(modifier = modifier.onSizeChanged { size -> anchorWidthPx = size.width }) {
        SelectAnchor(
            label = selectedLabel,
            isPlaceholder = isPlaceholder,
            expanded = expanded,
            onClickLabel = onClickLabel,
            onClick = { expanded = true },
        )
        if (expanded) {
            SelectMenu(
                anchorWidthPx = anchorWidthPx,
                options = options,
                selected = selected,
                onDismiss = { expanded = false },
                onSelect = { value ->
                    expanded = false
                    onSelect(value)
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
private fun <T> SelectMenu(
    anchorWidthPx: Int,
    options: List<ManyakSelectOption<T>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
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
                    .shadow(elevation = MenuShadowElevation, shape = ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                    .border(BorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                    .clip(ManyakTheme.shapes.control)
                    .padding(ManyakTheme.spacing.inline),
        ) {
            options.forEach { option ->
                SelectMenuItem(
                    label = option.label,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun SelectMenuItem(
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
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
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
                modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
                painter = painterResource(R.drawable.ic_check),
                // 선택 여부는 항목의 시맨틱이 이미 알린다. 아이콘까지 읽히면 같은 말이 두 번 나온다.
                contentDescription = null,
                tint = ManyakTheme.colors.text,
            )
        }
    }
}

@Composable
private fun SelectAnchor(
    label: String,
    isPlaceholder: Boolean,
    expanded: Boolean,
    onClickLabel: String?,
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
                .border(BorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                .padding(horizontal = ManyakTheme.spacing.controlHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = ManyakTheme.typography.bodyMedium,
            color = if (isPlaceholder) ManyakTheme.colors.textDisabled else ManyakTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.iconSmall),
            painter = painterResource(if (expanded) R.drawable.ic_angle_up else R.drawable.ic_angle_down),
            // 고른 값과 함께 한 줄로 읽히므로 아이콘에 따로 이름을 붙이지 않는다.
            contentDescription = null,
            tint = ManyakTheme.colors.textSubtle,
        )
    }
}

private val MenuShadowElevation = 4.dp

private val BorderWidth = 1.dp
