package app.manyak.create.keyword.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Rule
import org.junit.Test

class SupportingCharacterCollapseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun collapseHidesOnlyTargetFormAndExpandRestoresInput() {
        var state by mutableStateOf(
            CreateKeywordUiState(
                supportingCharacters =
                    listOf(
                        KeywordCharacter(1, name = "첫 번째 인물"),
                        KeywordCharacter(2, name = "두 번째 인물"),
                    ),
                providedTags = ProvidedTags.Loaded(emptyMap()),
            ),
        )
        compose.setContent {
            ManyakTheme {
                SupportingCharacterList(
                    state = state,
                    onIntent = { intent ->
                        state.supportingCharacterEvent(intent)?.let { state = reduceKeywordState(state, it) }
                    },
                    onOpenAddKeyword = {},
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
            }
        }
        compose.onAllNodesWithText("기본 정보").assertCountEquals(2)
        compose.onAllNodesWithText("접기")[0].performClick()
        compose.onAllNodesWithText("기본 정보").assertCountEquals(1)
        compose.onAllNodesWithText("펼치기")[0].performClick()
        compose.onAllNodesWithText("기본 정보").assertCountEquals(2)
        compose.onAllNodesWithText("첫 번째 인물").assertCountEquals(2)
    }
}
