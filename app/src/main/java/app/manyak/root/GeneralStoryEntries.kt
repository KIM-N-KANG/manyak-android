package app.manyak.root

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.manyak.core.navigation.ChatRoomRoute
import app.manyak.core.navigation.CreateKeywordRoute
import app.manyak.core.navigation.CreateMethodRoute
import app.manyak.core.navigation.GeneralCreateRoute
import app.manyak.core.navigation.GeneralSubmissionRoute
import app.manyak.core.navigation.PersonaCreateRoute
import app.manyak.core.navigation.StoryDetailRoute
import app.manyak.core.navigation.StoryEditRoute
import app.manyak.create.general.presentation.CreateMethodScreen
import app.manyak.create.general.presentation.GeneralEditorEntry
import app.manyak.create.general.presentation.GeneralStoryScreen
import app.manyak.story.detail.presentation.StoryDetailScreen
import java.util.UUID

internal fun EntryProviderScope<NavKey>.generalStoryEntries(
    backStack: MutableList<NavKey>,
    onSelectStudio: () -> Unit,
) {
    entry<CreateMethodRoute> {
        CreateMethodScreen(
            onClose = { backStack.pop() },
            onSelectSimple = { backStack.replaceTop(CreateKeywordRoute(UUID.randomUUID().toString())) },
            onSelectGeneral = { backStack.replaceTop(GeneralCreateRoute(UUID.randomUUID().toString())) },
        )
    }
    entry<GeneralCreateRoute> { route ->
        GeneralStoryScreen(
            entry = GeneralEditorEntry.Draft(route.draftId),
            onClose = {
                backStack.popToMainTabs()
                onSelectStudio()
            },
            onApproved = { storyId, chatId ->
                backStack.popToMainTabs()
                onSelectStudio()
                backStack.push(StoryDetailRoute(storyId))
                chatId?.let { backStack.push(ChatRoomRoute(it)) }
            },
        )
    }
    entry<GeneralSubmissionRoute> { route ->
        GeneralStoryScreen(
            entry = GeneralEditorEntry.Submission(route.submissionId),
            onClose = {
                backStack.popToMainTabs()
                onSelectStudio()
            },
            onApproved = { storyId, chatId ->
                backStack.popToMainTabs()
                onSelectStudio()
                backStack.push(StoryDetailRoute(storyId))
                chatId?.let { backStack.push(ChatRoomRoute(it)) }
            },
        )
    }
    entry<StoryEditRoute> { route ->
        GeneralStoryScreen(
            entry = GeneralEditorEntry.Edit(route.storyId),
            onClose = { backStack.pop() },
            onApproved = { _, _ -> backStack.pop() },
        )
    }
}

internal fun EntryProviderScope<NavKey>.storyDetailEntry(
    backStack: MutableList<NavKey>,
    onSelectStudio: () -> Unit,
) {
    entry<StoryDetailRoute> { route ->
        StoryDetailScreen(
            storyId = route.storyId,
            onEditStory = { backStack.push(StoryEditRoute(it)) },
            onBack = { backStack.pop() },
            onStoryDeleted = {
                backStack.popToMainTabs()
                onSelectStudio()
            },
            onEnterChat = { backStack.push(ChatRoomRoute(it)) },
            onCreatePersona = { backStack.push(PersonaCreateRoute(originStoryId = route.storyId)) },
        )
    }
}
