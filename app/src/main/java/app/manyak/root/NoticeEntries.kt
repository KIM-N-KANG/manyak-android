package app.manyak.root

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.NoticeDetailRoute
import app.manyak.core.navigation.NoticeListRoute
import app.manyak.notice.presentation.NoticeDetailScreen
import app.manyak.notice.presentation.NoticeListScreen

/** 공지 목록·상세. 셸 없이 전체 화면이고, 상세의 뒤로가기는 목록으로 돌아온다(푸시 진입은 목록을 먼저 쌓는다). */
internal fun EntryProviderScope<NavKey>.noticeEntries(backStack: MutableList<NavKey>) {
    entry<NoticeListRoute> {
        NoticeListScreen(
            onBack = { backStack.pop() },
            onOpenNotice = { noticeId -> backStack.push(NoticeDetailRoute(noticeId)) },
        )
    }
    entry<NoticeDetailRoute> { route ->
        NoticeDetailScreen(noticeId = route.noticeId, onBack = { backStack.pop() })
    }
}
