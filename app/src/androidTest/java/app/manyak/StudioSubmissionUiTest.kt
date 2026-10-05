package app.manyak

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.analytics.domain.NoOpAnalytics
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.StorySummary
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.report.domain.ReportRepository
import app.manyak.report.entity.StoryReportReason
import app.manyak.studio.domain.StudioRepository
import app.manyak.studio.entity.StorySubmission
import app.manyak.studio.entity.SubmissionStatus
import app.manyak.studio.presentation.StudioScreen
import app.manyak.studio.presentation.StudioViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudioSubmissionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardsAndCancellationUseAppComponentsAndKeepConfirmationOnRestore() {
        val repository = SubmissionUiRepository()
        lateinit var vm: StudioViewModel
        compose.runOnUiThread { vm = StudioViewModel(EmptyCreationProgress, repository, NoOpAnalytics, repository) }
        val dark = mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ManyakTheme(darkTheme = dark.value) {
                Surface {
                    StudioScreen(PaddingValues(16.dp), {}, {}, { _, _ -> }, viewModel = vm)
                }
            }
        }
        compose.onNodeWithText("검토 중").assertIsDisplayed()
        compose.onNodeWithText("수정이 필요한 곳이 2곳 있어요").assertIsDisplayed()
        compose.onNodeWithText("이미지를 바꿔 다시 등록해 주세요").assertIsDisplayed()
        compose.onNodeWithText("수정하기").assertDoesNotExist()
        capture("cards-light")
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("검토 중").assertIsDisplayed()
        capture("cards-dark")
        compose.runOnIdle { dark.value = false }
        compose.onAllNodesWithContentDescription("등록을 요청한 스토리 옵션 더보기")[0].performClick()
        compose.onNodeWithText("등록 취소").performClick()
        compose.onNodeWithText("등록을 취소할까요?").assertIsDisplayed()
        compose.onNodeWithText("검토 중인 스토리를 지우고 등록을 취소해요").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("등록을 취소할까요?").assertIsDisplayed()
        capture("cancel-confirmation")
        compose.onNodeWithText("등록 취소").performClick()
        compose.waitUntil { repository.deleted == listOf("pending") }
        compose.onNodeWithText("검토 중").assertDoesNotExist()
        compose.onNodeWithText("반려").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("등록을 요청한 스토리 옵션 더보기")[0].performClick()
        compose.onNodeWithText("삭제하기").performClick()
        compose.onNodeWithText("스토리를 삭제할까요?").assertIsDisplayed()
        compose.onNodeWithText("삭제하면 입력한 내용이 사라져요").assertIsDisplayed()
        capture("delete-confirmation")
        compose.onNodeWithText("삭제하기").performClick()
        compose.waitUntil { repository.deleted.size == 2 }
        assertEquals(listOf("pending", "rejected"), repository.deleted)
        compose.onNodeWithText("반려").assertDoesNotExist()
        compose.onNodeWithText("검토 실패").assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureStudio") != "true") return
        compose.waitForIdle()
        Log.i("Knk1520QA", name)
        Thread.sleep(2_000)
    }
}

private class SubmissionUiRepository :
    StudioRepository,
    ReportRepository {
    val deleted = mutableListOf<String>()
    private var items =
        listOf(
            StorySubmission("pending", SubmissionStatus.PENDING, "달빛 도서관", null, 1_791_162_123_000L, 0, false),
            StorySubmission("rejected", SubmissionStatus.REJECTED, "기억을 파는 상점", null, 1_791_158_523_000L, 2, false),
            StorySubmission("failed", SubmissionStatus.FAILED, "마지막 우주 우체부", null, 1_791_154_923_000L, 0, true),
        )

    override suspend fun submissions() = DomainResult.Success(items)

    override suspend fun myStories() = DomainResult.Success(emptyList<StorySummary>())

    override suspend fun deleteSubmission(submissionId: String): DomainResult<Unit> {
        deleted += submissionId
        items = items.filterNot { it.id == submissionId }
        return DomainResult.Success(Unit)
    }

    override suspend fun deleteStory(storyId: String) = error("Not used")

    override suspend fun reportStory(
        storyId: String,
        reason: StoryReportReason,
        detail: String?,
    ) = error("Not used")
}

private object EmptyCreationProgress : CreationProgressAccess {
    override val drafts = MutableStateFlow(emptyList<CreationProgressSummary>())
    override val completionRequests = MutableStateFlow(emptyList<CompletionRequestSummary>())

    override suspend fun discard(draftId: String) = false

    override suspend fun refreshCompletionRequests() = Unit

    override suspend fun retryCompletionRequest(requestId: String) = Unit

    override suspend fun deleteCompletionRequest(requestId: String) = false
}
