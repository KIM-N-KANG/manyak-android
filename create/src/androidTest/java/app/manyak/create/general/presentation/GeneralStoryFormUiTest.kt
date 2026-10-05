package app.manyak.create.general.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralErrorReason
import app.manyak.create.general.entity.GeneralField
import app.manyak.create.general.entity.GeneralFieldError
import app.manyak.create.general.entity.GeneralFieldTarget
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import app.manyak.create.general.entity.GeneralTab
import app.manyak.create.general.presentation.form.GeneralStoryFormContent
import app.manyak.create.general.presentation.image.GeneralImageCropSheet
import app.manyak.designsystem.theme.ManyakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class GeneralStoryFormUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun validationOpensTheFirstErrorTabAndExpandsCollapsedInput() {
        val character = GeneralCharacter()
        var errors by mutableStateOf(emptyList<GeneralFieldError>())
        var validationRequest by mutableIntStateOf(0)
        var form by mutableStateOf(GeneralStoryForm(supporting = listOf(character)))
        compose.setContent {
            ManyakTheme {
                GeneralStoryFormContent(
                    form = form,
                    errors = errors,
                    submitLabel = "등록하기",
                    onFormChange = { form = it },
                    onSubmit = {},
                    validationRequest = validationRequest,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithText("주변 인물 *").performScrollTo().performClick()
        compose.onNodeWithText("접기").performClick()
        compose.onNodeWithText("스토리 프로필 *").performScrollTo().performClick()
        compose.runOnIdle {
            errors =
                listOf(
                    GeneralFieldError(
                        GeneralFieldTarget(GeneralTab.SUPPORTING, GeneralField.NAME, character.id),
                        GeneralErrorReason.REQUIRED,
                    ),
                )
            validationRequest++
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("이름").assertIsDisplayed()
        compose.onNodeWithText("이름을 입력해 주세요").assertIsDisplayed()
        compose.onNodeWithText("접기").assertIsDisplayed()
    }

    @Test
    fun populatedDeletionAsksForConfirmationAndEmptyDeletionIsImmediate() {
        var form by mutableStateOf(
            GeneralStoryForm(supporting = listOf(GeneralCharacter(name = "첫 인물"), GeneralCharacter())),
        )
        compose.setContent {
            ManyakTheme {
                GeneralStoryFormContent(form, emptyList(), "등록하기", { form = it }, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("주변 인물 *").performScrollTo().performClick()
        compose.onAllNodesWithText("삭제")[0].performClick()
        compose.onNodeWithText("작성한 내용을 삭제할까요?").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle { assertEquals(2, form.supporting.size) }
        compose.onAllNodesWithText("삭제")[1].performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, form.supporting.size)
            assertEquals("첫 인물", form.supporting.single().name)
        }
        compose.onNodeWithText("작성한 내용을 삭제할까요?").assertDoesNotExist()
    }

    @Test
    fun compositionRecreationKeepsInputAndCollapsedTabState() {
        val restoration = StateRestorationTester(compose)
        var form by mutableStateOf(GeneralStoryForm())
        restoration.setContent {
            ManyakTheme {
                GeneralStoryFormContent(form, emptyList(), "등록하기", { form = it }, {}, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithContentDescription("제목").performTextInput("보존할 제목")
        compose.runOnIdle { assertEquals("보존할 제목", form.title) }
        compose.onNodeWithText("주변 인물 *").performScrollTo().performClick()
        compose.onNodeWithText("접기").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("펼치기").assertIsDisplayed()
        compose.onNodeWithText("스토리 프로필 *").performScrollTo().performClick()
        compose.onNodeWithText("보존할 제목").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancellingCropDoesNotReplaceExistingImageOrInvokeUpload() {
        val original = GeneralStoryImage(objectKey = "existing-image", previewUrl = "https://example.com/image.jpg")
        var image by mutableStateOf(original)
        var visible by mutableStateOf(true)
        var uploadCalled = false
        compose.setContent {
            ManyakTheme {
                if (visible) {
                    GeneralImageCropSheet(
                        path = "/nonexistent-crop-preview.jpg",
                        aspectRatio = 4f / 3f,
                        onDismiss = { visible = false },
                        onCropped = {
                            uploadCalled = true
                            image = GeneralStoryImage(localPath = it)
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("닫기").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(original, image)
            assertFalse(uploadCalled)
        }
    }
}
