package app.manyak.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 헤더 오른쪽 더보기 메뉴. 트리거 아이콘과 팝업 배치·모양을 소유하고, 항목은 호출부가 채운다.
 *
 * 펼침 상태는 구성 변경에서 살아남는다 — 회전했다고 열어 둔 메뉴가 닫히면 무엇을 누르려던 중이었는지
 * 사라진다.
 *
 * @param tint 트리거 아이콘 색. 스토리 상세처럼 배경이 스크롤에 따라 바뀌는 앱바가 직접 정한다.
 * @param content 항목 슬롯. 넘어오는 `dismiss` 를 항목의 동작과 함께 불러 메뉴를 닫는다.
 */
@Composable
fun ManyakOptionsMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = ManyakTheme.colors.text,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(ManyakTheme.sizes.control)
                    .clip(ManyakTheme.shapes.pill)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = contentDescription,
                        onClick = { expanded = true },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.icon),
                painter = painterResource(R.drawable.ic_more),
                contentDescription = contentDescription,
                tint = tint,
            )
        }
        if (expanded) {
            OptionsPopup(onDismiss = { expanded = false }, content = content)
        }
    }
}

/** 메뉴 항목 하나. */
@Composable
fun ManyakOptionsMenuItem(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
) {
    val color = if (isDanger) ManyakTheme.colors.textDanger else ManyakTheme.colors.text
    Row(
        modifier =
            modifier
                // 한 단어 항목이라 내용 폭만으로는 누를 자리가 좁다.
                .widthIn(min = MenuItemMinWidth)
                .clip(ManyakTheme.shapes.menuItem)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = ManyakTheme.spacing.controlHorizontal,
                    vertical = ManyakTheme.spacing.controlVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.compact),
    ) {
        Icon(
            modifier = Modifier.size(ManyakTheme.sizes.icon),
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
        )
        Text(text = label, style = ManyakTheme.typography.bodyMedium, color = color)
    }
}

/** 트리거 오른쪽 끝에 맞춰 아래로 연다 — 왼쪽 정렬로 열면 화면 밖으로 나간다. */
@Composable
private fun OptionsPopup(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val gapPx = with(LocalDensity.current) { ManyakTheme.spacing.inline.roundToPx() }
    val positionProvider =
        remember(gapPx) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset =
                    IntOffset(
                        x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0),
                        y = anchorBounds.bottom + gapPx,
                    )
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
                    .shadow(elevation = MenuShadowElevation, shape = ManyakTheme.shapes.control)
                    .background(ManyakTheme.colors.surfaceRaised, ManyakTheme.shapes.control)
                    .border(MenuBorderWidth, ManyakTheme.colors.border, ManyakTheme.shapes.control)
                    .clip(ManyakTheme.shapes.control)
                    .padding(ManyakTheme.spacing.compact),
        ) {
            content(onDismiss)
        }
    }
}

private val MenuShadowElevation = 8.dp

private val MenuBorderWidth = 1.dp

private val MenuItemMinWidth = 140.dp
