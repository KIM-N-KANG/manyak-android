package app.manyak.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 시트가 열린 뒤 잠금이 바뀌어도 뒤로가기가 그 값을 따르는지 본다. */
class ManyakBottomSheetBackTest {
    @get:Rule
    val compose = createComposeRule()

    private var locked by mutableStateOf(false)
    private var dismisses = 0

    private fun showSheet(lockedAtOpen: Boolean) {
        locked = lockedAtOpen
        compose.setContent {
            ManyakTheme {
                ManyakBottomSheet(onDismissRequest = { dismisses++ }, dismissEnabled = !locked) {
                    Text("본문")
                }
            }
        }
        compose.onNodeWithText("본문").assertIsDisplayed()
    }

    private fun pressBack() {
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
    }

    @Test
    fun backIsIgnoredWhenLockedAfterOpen() {
        showSheet(lockedAtOpen = false)
        locked = true
        compose.waitForIdle()
        pressBack()
        compose.runOnIdle { assertEquals(0, dismisses) }
    }

    @Test
    fun backDismissesWhenUnlockedAfterOpen() {
        showSheet(lockedAtOpen = true)
        pressBack()
        compose.runOnIdle { assertEquals(0, dismisses) }
        locked = false
        compose.waitForIdle()
        pressBack()
        compose.runOnIdle { assertEquals(1, dismisses) }
    }
}
