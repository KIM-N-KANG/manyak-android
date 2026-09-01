package app.manyak.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.manyak.core.ui.R
import app.manyak.core.ui.theme.ManyakTheme

/**
 * 고정 탭 하단 바.
 *
 * M3 [NavigationBar] 를 쓰면 하단 안전 영역·바 높이·최소 터치 타깃·탭 시맨틱이 따라온다.
 * 이 디자인 시스템에 없는 두 요소만 지운다.
 *
 * - **알약 모양 선택 표시자** — 표시자 색을 투명으로 둔다.
 * - **눌림 피드백** — [NavigationBarItem] 은 표시자 자리에 리플을 그리고 그 배치는 공개 API 로 바꿀
 *   수 없다. [LocalRippleConfiguration] 에 `null` 을 내려 이 하위 트리의 리플을 끈다. 탭을 누르면
 *   아이콘 모양과 색이 곧바로 바뀌므로 그 변화 자체가 반응이다.
 *
 * 대신 **위쪽 경계선을 더한다.** 바 배경이 화면 바탕과 같은 색이고 이 시스템은 그림자를 쓰지 않으므로,
 * 경계선이 없으면 콘텐츠가 바 아래로 흘러 들어갈 때 어디까지가 콘텐츠인지 드러나지 않는다.
 */
@Composable
fun ManyakNavigationBar(
    items: List<ManyakNavigationItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(thickness = 1.dp, color = ManyakTheme.colors.border)
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            NavigationBar(containerColor = ManyakTheme.colors.surface) {
                items.forEach { item -> NavigationTab(item) }
            }
        }
    }
}

/**
 * 탭 하나.
 *
 * 선택 색이 브랜드 초록이 아닌 이유는 하단 바가 늘 떠 있는 chrome 이기 때문이다 — 초록을 상시
 * 띄우면 화면 안에서 정작 눌러야 할 주 동작과 강조가 겹친다.
 */
@Composable
private fun RowScope.NavigationTab(item: ManyakNavigationItem) {
    NavigationBarItem(
        selected = item.selected,
        onClick = item.onSelect,
        icon = {
            Icon(
                modifier = Modifier.size(ManyakTheme.sizes.tabIcon),
                painter = painterResource(if (item.selected) item.selectedIconRes else item.unselectedIconRes),
                contentDescription = null,
            )
        },
        label = {
            Text(
                text = stringResource(item.nameRes),
                style = ManyakTheme.typography.labelSmall,
                maxLines = 1,
            )
        },
        colors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = ManyakTheme.colors.text,
                selectedTextColor = ManyakTheme.colors.text,
                unselectedIconColor = ManyakTheme.colors.textSubtle,
                unselectedTextColor = ManyakTheme.colors.textSubtle,
                indicatorColor = Color.Transparent,
            ),
    )
}

@Preview(name = "하단 내비게이션 · 라이트")
@Composable
private fun ManyakNavigationBarPreview() {
    ManyakTheme(darkTheme = false) {
        ManyakNavigationBar(items = previewItems())
    }
}

@Preview(name = "하단 내비게이션 · 다크")
@Composable
private fun ManyakNavigationBarDarkPreview() {
    ManyakTheme(darkTheme = true) {
        ManyakNavigationBar(items = previewItems())
    }
}

@Composable
private fun previewItems(): List<ManyakNavigationItem> =
    listOf(
        ManyakNavigationItem(
            selectedIconRes = R.drawable.ic_nav_home_filled,
            unselectedIconRes = R.drawable.ic_nav_home_outline,
            nameRes = R.string.main_tab_home,
            selected = true,
            onSelect = {},
        ),
        ManyakNavigationItem(
            selectedIconRes = R.drawable.ic_nav_chat_filled,
            unselectedIconRes = R.drawable.ic_nav_chat_outline,
            nameRes = R.string.main_tab_chat,
            selected = false,
            onSelect = {},
        ),
        ManyakNavigationItem(
            selectedIconRes = R.drawable.ic_nav_studio_filled,
            unselectedIconRes = R.drawable.ic_nav_studio_outline,
            nameRes = R.string.main_tab_studio,
            selected = false,
            onSelect = {},
        ),
        ManyakNavigationItem(
            selectedIconRes = R.drawable.ic_nav_my_filled,
            unselectedIconRes = R.drawable.ic_nav_my_outline,
            nameRes = R.string.main_tab_my,
            selected = false,
            onSelect = {},
        ),
    )
