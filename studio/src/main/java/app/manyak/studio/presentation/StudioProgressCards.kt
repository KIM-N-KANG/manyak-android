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
    // 첫 조회 중에는 초안과 제출본을 스켈레톤 뒤에 두었다가 내 스토리와 함께 보인다.
    val drafts = if (state.isListLoading) emptyList() else state.drafts
    val submissions = if (state.isListLoading) emptyList() else state.submissions
    // 기기 안 기록 뒤에 다른 곳에서 저장한 서버 초안은 같은 ID 로 따로 보인다.
    items(drafts, key = { draft ->
        "$DRAFT_KEY_PREFIX${if (draft.onServer) "server:" else ""}${draft.draftId}"
    }) { draft ->
        CreationProgressCard(
            kind =
                CreationProgressCardKind.Draft(
                    draft.stage,
                    draft.resumePoint,
                    draft.title,
                    draft.oneLineIntro,
                    draft.thumbnailUrl,
                ),
            savedAt = draft.createdAt,
            onPrimaryAction = { onIntent(StudioIntent.ResumeCreation(draft.draftId, draft.onServer)) },
            onOptionsClick = {
                onIntent(
                    StudioIntent.OpenCardOptions(StudioCard.Draft(draft.draftId, draft.onServer)),
                )
            },
        )
    }
    items(submissions, key = { "submission:${it.id}" }) { submission ->
        SubmissionCard(
            submission = submission,
            onEdit = { onIntent(StudioIntent.EditSubmission(submission.id)) },
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
