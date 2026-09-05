package app.manyak.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import app.manyak.designsystem.theme.ManyakTheme

/**
 * 목적지 하나가 자기 상태를 갖게 하는 최소 구성. 바깥부터 배경 → `rememberSaveable` → ViewModel
 * 순이다. 화면 배치와 수명 처리에 필요한 나머지는 `NavDisplay` 가 안에서 붙이므로 여기서 다시
 * 넣지 않는다.
 *
 * 배경을 목적지마다 까는 이유 — 화면들은 루트 `Surface` 색에 기대어 자기 배경을 그리지 않는데,
 * 밀기 전환에서는 두 목적지가 한 프레임에 겹쳐 아래 화면이 위 화면 사이로 비친다.
 *
 * **백스택 하나마다 자기 decorator 집합을 가져야 한다.** 호출 위치마다 다른 인스턴스가 되므로,
 * 탭처럼 백스택이 여럿일 때는 각 탭에서 따로 호출해야 한 탭의 pop 이 다른 탭의 상태를 지우지 않는다.
 */
@Composable
fun rememberManyakEntryDecorators(): List<NavEntryDecorator<NavKey>> {
    val surface = ManyakTheme.colors.surface
    val background =
        remember(surface) {
            NavEntryDecorator<NavKey> { entry ->
                Box(Modifier.fillMaxSize().background(surface)) { entry.Content() }
            }
        }
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStore = rememberViewModelStoreNavEntryDecorator<NavKey>(rememberViewModelStoreProvider())
    return remember(background, saveableState, viewModelStore) { listOf(background, saveableState, viewModelStore) }
}
