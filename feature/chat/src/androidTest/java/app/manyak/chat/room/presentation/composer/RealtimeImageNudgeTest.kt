package app.manyak.chat.room.presentation.composer

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.chat.entity.ChatInputMode
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RealtimeImageNudgeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun enablingHighlightedSwitchDismissesNudgeAndLeavesSheetOpen() {
        showSheet()
        val highlightedBounds = compose.onNodeWithContentDescription("실시간 이미지").fetchSemanticsNode().boundsInWindow
        compose
            .onNodeWithContentDescription("실시간 이미지")
            .assertIsOff()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText("실시간 이미지를 켜보세요").assertDoesNotExist()
        compose.onNodeWithText("채팅 설정").assertIsDisplayed()
        compose.onNodeWithText("AI 추천 입력").assertIsDisplayed()
        val row = compose.onNode(isToggleable() and hasText("실시간 이미지")).assertIsOn()
        val rowBounds = row.fetchSemanticsNode().boundsInWindow
        assertEquals(highlightedBounds.top, rowBounds.top, 1f)
        assertEquals(highlightedBounds.bottom, rowBounds.bottom, 1f)
    }

    @Test
    fun confirmDismissesNudgeWithoutEnablingImage() {
        showSheet()
        compose.onNodeWithText("AI 추천 입력").assertDoesNotExist()
        compose.onNodeWithText("확인").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("실시간 이미지를 켜보세요").assertDoesNotExist()
        compose.onNodeWithText("채팅 설정").assertIsDisplayed()
        compose.onNode(isToggleable() and hasText("실시간 이미지")).assertIsOff()
    }

    @Test
    fun backDismissesNudgeBeforeSheet() {
        showSheet()
        compose.onNodeWithText("실시간 이미지를 켜보세요").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("실시간 이미지를 켜보세요").assertDoesNotExist()
        compose.onNodeWithText("채팅 설정").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("채팅 설정").assertDoesNotExist()
    }

    @Test
    fun cardBodyKeepsNudgeAndDimDismissesOnlyNudge() {
        showSheet()
        compose
            .onNodeWithText("대화에 따라 달라지는 인물의 모습을 볼 수 있어요")
            .performTouchInput { click() }
        compose.onNodeWithText("실시간 이미지를 켜보세요").assertIsDisplayed()
        compose.onNode(isPopup()).performTouchInput { click(Offset(center.x, 1f)) }
        compose.waitForIdle()
        compose.onNodeWithText("실시간 이미지를 켜보세요").assertDoesNotExist()
        compose.onNodeWithText("채팅 설정").assertIsDisplayed()
    }

    private fun showSheet() {
        compose.setContent {
            var sheetOpen by remember { mutableStateOf(true) }
            var nudgeOpen by remember { mutableStateOf(true) }
            var enabled by remember { mutableStateOf(false) }
            ManyakTheme {
                if (sheetOpen) {
                    ChatSettingsSheet(
                        realtimeImageEnabled = enabled,
                        choicesEnabled = true,
                        mode = ChatInputMode.BLOCK,
                        onRealtimeImageEnabledChange = { enabled = it },
                        onChoicesEnabledChange = {},
                        onModeChange = {},
                        onDismiss = { sheetOpen = false },
                        nudgeOpen = nudgeOpen,
                        onNudgeDismiss = { nudgeOpen = false },
                    )
                }
            }
        }
        compose.waitForIdle()
    }
}
