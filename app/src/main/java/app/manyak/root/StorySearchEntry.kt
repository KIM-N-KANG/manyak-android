package app.manyak.root

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StorySearchRoute
import app.manyak.home.search.presentation.StorySearchScreen

internal fun EntryProviderScope<NavKey>.storySearchEntry(backStack: MutableList<NavKey>) {
    entry<StorySearchRoute> {
        StorySearchScreen(
            onBack = { backStack.pop() },
            onOpenStory = { storyId -> backStack.push(StoryDetailRoute(storyId)) },
        )
    }
}
