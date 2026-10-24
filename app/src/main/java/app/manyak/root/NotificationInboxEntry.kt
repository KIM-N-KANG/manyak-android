package app.manyak.root

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.NotificationInboxRoute
import app.manyak.core.navigation.NotificationSettingsRoute
import app.manyak.core.navigation.StudioRoute
import app.manyak.notification.inbox.presentation.NotificationInboxScreen

internal fun EntryProviderScope<NavKey>.notificationInboxEntry(
    backStack: MutableList<NavKey>,
    onOpenStudio: () -> Unit,
) {
    entry<NotificationInboxRoute> {
        NotificationInboxScreen(
            onBack = { backStack.pop() },
            onOpenSettings = { backStack.push(NotificationSettingsRoute) },
            onNavigate = { destination ->
                if (destination == StudioRoute) {
                    backStack.popToMainTabs()
                    onOpenStudio()
                } else {
                    backStack.push(destination)
                }
            },
        )
    }
}
