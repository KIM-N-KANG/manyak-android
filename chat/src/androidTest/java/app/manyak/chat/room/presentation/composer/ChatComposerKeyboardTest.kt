package app.manyak.chat.room.presentation.composer

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.manyak.designsystem.component.clearFocusOnTap
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatComposerKeyboardTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var view: View

    @Test
    fun editingButtons_preserveKeyboard_andBackgroundDismissesIt() {
        compose.setContent {
            ManyakTheme {
                val localView = LocalView.current
                SideEffect { view = localView }
                var state by remember { mutableStateOf(ChatComposerState()) }
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).clearFocusOnTap()) {
                    Spacer(Modifier.testTag("background").fillMaxWidth().height(ManyakTheme.sizes.control))
                    ChatComposer(
                        state = state,
                        choicesEnabled = true,
                        hasSuggestions = false,
                        isStreaming = false,
                        actions =
                            ChatComposerActions(
                                onPlainTextChange = { state = state.copy(plainText = it) },
                                onBlockValueChange = { id, text ->
                                    state =
                                        state.copy(blocks = state.blocks.updateBlock(id, text))
                                },
                                onAddBlock = { state = state.copy(blocks = state.blocks.addBlock(it)) },
                                onRemoveBlock = { state = state.copy(blocks = state.blocks.removeBlock(it)) },
                                onOpenSettings = {},
                                onSend = {},
                                onSendRandomSuggestion = {},
                                onLockedTap = {},
                            ),
                    )
                }
            }
        }
        fields()[0].performTouchInput { click() }
        compose.waitUntil(5_000) { keyboardVisible() }
        compose.onNodeWithText("상황 추가").performTouchInput { click() }
        compose.waitForIdle()
        fields()[0].assertIsFocused()
        assertTrue(keyboardVisible())
        compose.onNodeWithText("대사 추가").performTouchInput { click() }
        compose.waitForIdle()
        fields()[0].assertIsFocused()
        assertTrue(keyboardVisible())
        // 포커스가 없는 칸을 지운 다음, 현재 입력 중인 칸도 지운다.
        compose.onAllNodesWithContentDescription("입력 삭제")[1].performTouchInput { click() }
        compose.waitForIdle()
        fields()[0].assertIsFocused()
        assertTrue(keyboardVisible())
        compose.onAllNodesWithContentDescription("입력 삭제")[0].performTouchInput { click() }
        compose.waitForIdle()
        fields()[0].assertIsFocused()
        assertTrue(keyboardVisible())
        compose.onNodeWithTag("background").performTouchInput { click() }
        compose.waitUntil(5_000) { !keyboardVisible() }
        fields()[1].performTouchInput { click() }
        compose.waitUntil(5_000) { keyboardVisible() }
        compose.onAllNodesWithContentDescription("입력 삭제")[1].performTouchInput { click() }
        compose.waitForIdle()
        fields()[0].assertIsFocused()
        assertTrue(keyboardVisible())
        compose.onAllNodesWithContentDescription("입력 삭제")[0].performTouchInput { click() }
        compose.waitUntil(5_000) { !keyboardVisible() }
    }

    private fun fields() = compose.onAllNodes(hasSetTextAction())

    private fun keyboardVisible(): Boolean =
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
}
