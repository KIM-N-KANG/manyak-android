package app.manyak

import android.os.ParcelFileDescriptor
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.designsystem.component.ManyakBottomSheet
import app.manyak.designsystem.component.ManyakTextField
import app.manyak.designsystem.component.clearFocusOnTap
import app.manyak.designsystem.component.keepKeyboardOnTap
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeyboardFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View
    private val focusEvents = mutableListOf<Pair<Int, Boolean>>()
    private var outsideClicks = 0
    private lateinit var scrollState: ScrollState

    @Test
    fun stateFields_transferFocusWithoutHidingKeyboard() {
        setFields(stateBased = true)
        verifyFocusTransfer()
    }

    @Test
    fun valueFields_transferFocusWithoutHidingKeyboard() {
        setFields(stateBased = false)
        verifyFocusTransfer()
    }

    @Test
    fun outsideButton_dismissesKeyboardButScrollingPreservesIt() {
        setFields(stateBased = false)
        tap("field0")
        compose.waitUntil(5_000) { keyboardVisible() }
        compose.onNodeWithTag("scroll").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("스크롤이 동작해야 합니다", scrollState.value > 0)
        compose.onNodeWithTag("field0").assertIsFocused()
        assertTrue("스크롤 중 키보드를 유지해야 합니다", keyboardVisible())
        verifyOutsideButton()
    }

    @Test
    fun bottomSheet_usesItsOwnFocusManager() {
        compose.setContent {
            ManyakTheme {
                ManyakBottomSheet(onDismissRequest = {}) { ModalFieldAndButton() }
            }
        }
        verifyModalFocus()
    }

    @Test
    fun dialog_usesItsOwnFocusManager() {
        compose.setContent {
            ManyakTheme {
                AlertDialog(
                    modifier = Modifier.clearFocusOnTap(),
                    onDismissRequest = {},
                    confirmButton = {},
                    text = { Column { ModalFieldAndButton() } },
                )
            }
        }
        verifyModalFocus()
    }

    @Composable
    private fun ModalFieldAndButton() {
        val localView = LocalView.current
        SideEffect { view = localView }
        var value by remember { mutableStateOf("") }
        ManyakTextField(value, { value = it }, "", Modifier.testTag("field0"))
        OutsideButton()
    }

    @Composable
    private fun OutsideButton() {
        TextButton(onClick = { outsideClicks++ }, modifier = Modifier.testTag("button")) { Text("Outside") }
    }

    private fun verifyModalFocus() {
        tap("field0")
        compose.waitUntil(5_000) { keyboardVisible() }
        tap("field0")
        compose.onNodeWithTag("field0").assertIsFocused()
        assertTrue(keyboardVisible())
        verifyOutsideButton()
    }

    private fun verifyOutsideButton() {
        tap("button")
        assertEquals(1, outsideClicks)
        compose.onNodeWithTag("field0").assertIsNotFocused()
        compose.waitUntil(5_000) { !keyboardVisible() }
    }

    private fun setFields(stateBased: Boolean) {
        compose.setContent {
            ManyakTheme {
                val localView = LocalView.current
                SideEffect { view = localView }
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .clearFocusOnTap(),
                ) {
                    repeat(2) { index ->
                        val modifier =
                            Modifier
                                .testTag("field$index")
                                .height(ManyakTheme.sizes.control)
                                .onFocusChanged { focusEvents += index to it.isFocused }
                        if (stateBased) {
                            BasicTextField(state = rememberTextFieldState(), modifier = modifier.keepKeyboardOnTap())
                        } else {
                            var value by remember { mutableStateOf("") }
                            ManyakTextField(value, { value = it }, "", modifier)
                        }
                    }
                    Spacer(Modifier.testTag("background").fillMaxWidth().height(ManyakTheme.sizes.control))
                    OutsideButton()
                    val scroll = rememberScrollState()
                    SideEffect { scrollState = scroll }
                    Column(
                        Modifier
                            .testTag("scroll")
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scroll),
                    ) {
                        Spacer(Modifier.fillMaxWidth().height(ManyakTheme.sizes.control * 30))
                    }
                }
            }
        }
    }

    private fun verifyFocusTransfer() {
        tap("field0")
        compose.waitUntil(5_000) { keyboardVisible() }
        // 최초 표시 애니메이션이 끝난 뒤 전환 요청만 수집한다.
        compose.waitForIdle()
        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        repeat(3) {
            tap("field1")
            compose.onNodeWithTag("field1").assertIsFocused()
            val beforeRetap = focusEvents.toList()
            tap("field1")
            assertEquals("재터치 시 포커스가 해제되면 안 됩니다", beforeRetap, focusEvents)
            tap("field0")
            compose.onNodeWithTag("field0").assertIsFocused()
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val command = "logcat -d -v brief -T '$since' ImeTracker:I '*:S'"
        val logs =
            ParcelFileDescriptor
                .AutoCloseInputStream(automation.executeShellCommand(command))
                .bufferedReader()
                .use { it.readText() }
        assertFalse(
            "입력란 전환 중 키보드 숨김 요청이 발생했습니다:\n$logs",
            logs.lineSequence().any { "app.manyak:" in it && "onRequestHide" in it },
        )
        tap("background")
        compose.onNodeWithTag("field0").assertIsNotFocused()
        compose.waitUntil(5_000) { !keyboardVisible() }
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun keyboardVisible(): Boolean =
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
}
