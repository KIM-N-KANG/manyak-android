package app.manyak.studio.presentation

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.studio.presentation.component.CreationProgressCard
import app.manyak.studio.presentation.component.CreationProgressCardKind
import app.manyak.studio.presentation.component.SubmissionCard

internal fun LazyListScope.progressCards(
    state: StudioUiState,
    onOpenStory: (String) -> Unit,
    onIntent: (StudioIntent) -> Unit,
) {
    items(state.drafts, key = { draft -> "$DRAFT_KEY_PREFIX${draft.draftId}" }) { draft ->
        CreationProgressCard(
            kind = CreationProgressCardKind.Draft(draft.stage, draft.resumePoint),
            savedAt = draft.createdAt,
            onPrimaryAction = { onIntent(StudioIntent.ResumeCreation(draft.draftId)) },
            onOptionsClick = { onIntent(StudioIntent.OpenCardOptions(StudioCard.Draft(draft.draftId))) },
        )
    }
    items(state.submissions, key = { "submission:${it.id}" }) { submission ->
        SubmissionCard(
            submission = submission,
            onOptionsClick = { onIntent(StudioIntent.OpenCardOptions(StudioCard.Submission(submission))) },
        )
    }
    items(state.completionRequests, key = { request ->
        "$COMPLETION_KEY_PREFIX${request.requestId}"
    }) { request ->
        CompletionRequestRow(request = request, onOpenStory = onOpenStory, onIntent = onIntent)
    }
}

/** 요청 상태별 카드. 완성 중에는 아무 동작도 없고, 완료·실패만 각각 상세 진입과 재시도·삭제를 연다. */
@Composable
private fun CompletionRequestRow(
    request: CompletionRequestSummary,
    onOpenStory: (String) -> Unit,
    onIntent: (StudioIntent) -> Unit,
) {
    when (request.status) {
        CompletionRequestStatus.PENDING ->
            CreationProgressCard(kind = CreationProgressCardKind.Completing, savedAt = request.createdAt)

        CompletionRequestStatus.COMPLETED ->
            CreationProgressCard(
                kind = CreationProgressCardKind.Completed(request.storyTitle.orEmpty()),
                onClick = request.storyId?.let { storyId -> { onOpenStory(storyId) } },
            )

        CompletionRequestStatus.FAILED ->
            CreationProgressCard(
                kind = CreationProgressCardKind.Failed,
                savedAt = request.createdAt,
                onPrimaryAction = { onIntent(StudioIntent.RetryCompletion(request.requestId)) },
                onOptionsClick = {
                    onIntent(StudioIntent.OpenCardOptions(StudioCard.FailedRequest(request.requestId)))
                },
            )
    }
}

private const val DRAFT_KEY_PREFIX = "draft:"
private const val COMPLETION_KEY_PREFIX = "completion:"
