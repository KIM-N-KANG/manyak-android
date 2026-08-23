package app.manyak.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * 목적지 하나가 자기 상태를 갖게 하는 최소 구성. `rememberSaveable` 값은 앞의 decorator 가,
 * ViewModel 은 뒤의 decorator 가 목적지 수명에 묶는다. 화면 배치와 수명 처리에 필요한 나머지는
 * `NavDisplay` 가 안에서 붙이므로 여기서 다시 넣지 않는다.
 *
 * **백스택 하나마다 자기 decorator 집합을 가져야 한다.** 호출 위치마다 다른 인스턴스가 되므로,
 * 탭처럼 백스택이 여럿일 때는 각 탭에서 따로 호출해야 한 탭의 pop 이 다른 탭의 상태를 지우지 않는다.
 */
@Composable
fun rememberManyakEntryDecorators(): List<NavEntryDecorator<NavKey>> {
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStore = rememberViewModelStoreNavEntryDecorator<NavKey>(rememberViewModelStoreProvider())
    return remember(saveableState, viewModelStore) { listOf(saveableState, viewModelStore) }
}
