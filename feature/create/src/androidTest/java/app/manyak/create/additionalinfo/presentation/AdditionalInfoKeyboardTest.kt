package app.manyak.create.additionalinfo.presentation

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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.manyak.designsystem.component.clearFocusOnTap
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdditionalInfoKeyboardTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var view: View

    @Test
    fun editingActions_preserveKeyboard_andOutsideDismissesIt() {
        compose.setContent {
            ManyakTheme {
                val localView = LocalView.current
                SideEffect { view = localView }
                var state by remember {
                    mutableStateOf(
                        CreateAdditionalInfoUiState(
                            storylines = listOf(AdditionalInfoStoryline(1, "테스트 스토리", listOf("추천 정보"))),
                            additionalInfos = listOf(AdditionalInfoInput(0)),
                        ),
                    )
                }
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).clearFocusOnTap()) {
                    Spacer(Modifier.testTag("outside").fillMaxWidth().height(ManyakTheme.sizes.control))
                    AdditionalInfoList(
                        storylineIndex = 0,
                        state = state,
                        onIntent = { intent ->
                            state =
                                when (intent) {
                                    CreateAdditionalInfoIntent.AddInput ->
                                        state.copy(
                                            additionalInfos =
                                                state.additionalInfos + AdditionalInfoInput(state.nextInputId),
                                            nextInputId = state.nextInputId + 1,
                                        )
                                    is CreateAdditionalInfoIntent.RemoveInput ->
                                        state.copy(
                                            additionalInfos =
                                                state.additionalInfos.filterNot {
                                                    it.id == intent.inputId
                                                },
                                        )
                                    is CreateAdditionalInfoIntent.ToggleRecommendation ->
                                        state.copy(
                                            selectedRecommendations =
                                                if (intent.text in state.selectedRecommendations) {
                                                    state.selectedRecommendations - intent.text
                                                } else {
                                                    state.selectedRecommendations + intent.text
                                                },
                                        )
                                    else -> state
                                }
                        },
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("추가 정보 1").performScrollTo().performTouchInput { click() }
        compose.waitUntil(5_000) { keyboardVisible() }
        compose.onNodeWithText("추천 정보").performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("추천 정보").assertIsOn()
        verifyFocused()
        compose.onNodeWithText("더보기").performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        verifyFocused()
        compose.onNodeWithText("접기").performTouchInput { click() }
        compose.onNodeWithText("정보 추가").performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        verifyFocused()
        compose.onNodeWithContentDescription("추가 정보 1 삭제").performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        verifyFocused()
        compose.onNodeWithTag("outside").performTouchInput { click() }
        compose.waitUntil(5_000) { !keyboardVisible() }
        compose.onNodeWithContentDescription("추가 정보 1").performTouchInput { click() }
        compose.waitUntil(5_000) { keyboardVisible() }
        compose.onNodeWithContentDescription("추가 정보 1 삭제").performTouchInput { click() }
        compose.waitUntil(5_000) { !keyboardVisible() }
    }

    private fun verifyFocused() {
        compose.onNodeWithContentDescription("추가 정보 1").assertIsFocused()
        assertTrue(keyboardVisible())
    }

    private fun keyboardVisible(): Boolean =
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
}
