package app.manyak.chat.room.presentation.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.designsystem.R as DesignsystemR

/**
 * 컴포저 툴바의 설정 메뉴. 아이콘 버튼을 누르면 그 아래에 라디오 목록이 열린다.
 *
 * 펼침 상태를 이 컴포넌트가 드는 것은 `ManyakSelectField` 와 같은 판단이다 — 밖에서 알 필요가 없는
 * 표현 상태이고, 호출부마다 두면 여는 규칙이 갈린다.
 */
@Composable
internal fun <T> ComposerMenu(
    iconRes: Int,
    contentDescription: String,
    options: List<ComposerMenuOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = ManyakTheme.colors.textSubtle,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ComposerIconButton(
            iconRes = iconRes,
            contentDescription = contentDescription,
            tint = tint,
            enabled = enabled,
            onClick = { expanded = true },
        )
        if (expanded) {
            ComposerMenuContent(
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
 * 컴포저 툴바는 화면 아래에 붙어 있어 M3 기본 메뉴처럼 아래로 열면 화면 밖으로 나간다.
 * 앵커 **위**로 여는 위치를 직접 계산한다.
 */
@Composable
private fun <T> ComposerMenuContent(
    options: List<ComposerMenuOption<T>>,
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
                ): IntOffset {
                    val x = (anchorBounds.left).coerceAtMost(windowSize.width - popupContentSize.width)
                    return IntOffset(x = x.coerceAtLeast(0), y = anchorBounds.top - popupContentSize.height - gapPx)
                }
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
                    .width(MenuWidth)
                    .shadow(MenuShadowElevation, ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                    .border(BorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                    .clip(ManyakTheme.shapes.control)
                    .padding(ManyakTheme.spacing.inline),
        ) {
            options.forEach { option ->
                ComposerMenuItem(
                    option = option,
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun <T> ComposerMenuItem(
    option: ComposerMenuOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ManyakTheme.shapes.menuItem)
                .background(if (selected) ManyakTheme.colors.backgroundNeutral else ManyakTheme.colors.surfaceRaised)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.hairline),
        ) {
            Text(text = option.label, style = ManyakTheme.typography.bodyMedium, color = ManyakTheme.colors.text)
            Text(
                text = option.description,
                style = ManyakTheme.typography.labelSmall,
                color = ManyakTheme.colors.textSubtle,
            )
        }
        // 고르지 않은 항목도 같은 자리를 비워 둔다 — 선택이 옮겨 다녀도 글자 폭이 흔들리지 않는다.
        Box(modifier = Modifier.size(ManyakTheme.sizes.iconSmall)) {
            if (selected) {
                Icon(
                    painter = painterResource(DesignsystemR.drawable.ic_check),
                    // 선택 여부는 항목의 시맨틱이 이미 알린다.
                    contentDescription = null,
                    tint = ManyakTheme.colors.text,
                )
            }
        }
    }
}

/** 메뉴 폭. 최소가 아니라 고정이다 — 열어 두면 가장 긴 설명이 한 줄로 펴져 화면을 다 먹는다. */
private val MenuWidth = 208.dp
private val MenuShadowElevation = 8.dp
private val BorderWidth = 1.dp
