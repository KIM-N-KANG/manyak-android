package app.manyak.root

import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.MainTabsRoute

/** 연타로 같은 목적지가 두 번 쌓이지 않는다. 같은 키가 스택에 두 번 있어야 하는 흐름은 없다. */
internal fun MutableList<NavKey>.push(key: NavKey) {
    if (key !in this) add(key)
}

/** 루트는 걷어내지 않는다 — 뒤로가기 연타가 빈 백스택을 만들면 NavDisplay 가 죽는다. */
internal fun MutableList<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/** 한 번 쓰고 끝나는 퍼널 단계를 모두 걷어내 셸만 남긴다. */
internal fun MutableList<NavKey>.popToMainTabs() {
    while (size > 1 && lastOrNull() != MainTabsRoute) removeLastOrNull()
}
