package app.manyak.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
 * 헤더 오른쪽 옵션 메뉴. **채팅 삭제의 유일한 진입점**이고 항목도 그것 하나다.
 *
 * 웹 헤더의 공유 버튼은 앱에 두지 않는다 — 공유 URL 을 앱이 열지 웹으로 넘길지가 정해지지 않았다.
 */
@Composable
internal fun ChatRoomOptionsMenu(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.chat_room_options)

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .size(ManyakTheme.sizes.control)
                    .clip(ManyakTheme.shapes.pill)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = { expanded = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.icon),
                painter = painterResource(R.drawable.ic_more),
                contentDescription = label,
                tint = ManyakTheme.colors.text,
            )
        }
        if (expanded) {
            OptionsPopup(
                onDismiss = { expanded = false },
                onDelete = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** 트리거 오른쪽 끝에 맞춰 아래로 연다 — 왼쪽 정렬로 열면 화면 밖으로 나간다. */
@Composable
private fun OptionsPopup(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
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
            DeleteMenuItem(onClick = onDelete)
        }
    }
}

/** 파괴적 항목이라 아이콘·글자를 danger 색으로 둔다. 확인은 다이얼로그가 한 번 더 묻는다. */
@Composable
private fun DeleteMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = null,
            tint = ManyakTheme.colors.textDanger,
        )
        Text(
            text = stringResource(R.string.chat_room_delete),
            style = ManyakTheme.typography.bodyMedium,
            color = ManyakTheme.colors.textDanger,
        )
    }
}

private val MenuShadowElevation = 8.dp

private val MenuBorderWidth = 1.dp

private val MenuItemMinWidth = 140.dp
