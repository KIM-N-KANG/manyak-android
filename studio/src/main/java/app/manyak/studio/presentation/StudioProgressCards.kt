package app.manyak.studio.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.manyak.common.entity.story.CompletionFailure
import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.designsystem.theme.ManyakTheme
import app.manyak.studio.presentation.component.CreationProgressCard
import app.manyak.studio.presentation.component.CreationProgressCardKind
import app.manyak.studio.presentation.component.SubmissionCard
import app.manyak.common.R as CommonR
import app.manyak.studio.R as StudioR

/** 진행 중 구역. 초안과 검수 제출본이며 카드가 있을 때만 구역 제목과 개수를 둔다. */
internal fun LazyListScope.progressCards(
    state: StudioUiState,
    onIntent: (StudioIntent) -> Unit,
) {
    // 첫 조회 중에는 초안과 제출본을 스켈레톤 뒤에 두었다가 내 스토리와 함께 보인다.
    val drafts = if (state.isListLoading) emptyList() else state.drafts
    val submissions = if (state.isListLoading) emptyList() else state.submissions
    val count = drafts.size + submissions.size
    if (count > 0) {
        item(key = PROGRESS_SECTION_KEY) {
            StudioSectionTitle(title = stringResource(StudioR.string.studio_section_progress), count = count)
        }
    }
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
}

/**
 * 내 스토리 구역의 제목과 맨 위 완성 중 카드. 완성 중 카드를 개수에 넣어 두면 완성된 스토리로 바뀔 때
 * 구역 제목과 개수가 그대로이고 카드도 같은 자리에서 바뀐다. 그 아래 스토리 카드는 호출부가 잇는다.
 */
internal fun LazyListScope.storySectionHead(
    state: StudioUiState,
    storyCount: Int,
    onOpenStory: (String) -> Unit,
) {
    val count = state.completionRequests.size + storyCount
    if (count > 0) {
        item(key = STORY_SECTION_KEY) {
            StudioSectionTitle(
                title = stringResource(StudioR.string.studio_section_stories),
                count = count,
                following = state.hasProgressSection,
            )
        }
    }
    items(state.completionRequests, key = { request ->
        "$COMPLETION_KEY_PREFIX${request.requestId}"
    }) { request ->
        CompletionRequestRow(request = request, onOpenStory = onOpenStory)
    }
}

/** 진행 중 구역이 보이는지. 내 스토리 구역 제목이 그 뒤에 오면 위를 더 띄운다. */
private val StudioUiState.hasProgressSection: Boolean
    get() = !isListLoading && (drafts.isNotEmpty() || submissions.isNotEmpty())

/**
 * 구역 제목과 그 옆의 작은 개수. 크기가 다른 두 글자를 기준선에 맞추면 개수가 내려앉아 보여 세로 가운데에 맞춘다.
 * 앞 구역 뒤에 오면([following]) 위 여백을 넓혀 구역을 가른다.
 */
@Composable
private fun StudioSectionTitle(
    title: String,
    count: Int,
    following: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = ManyakTheme.spacing.gutter,
                    end = ManyakTheme.spacing.gutter,
                    top = if (following) ManyakTheme.spacing.section else ManyakTheme.spacing.compact,
                    bottom = ManyakTheme.spacing.inline,
                ).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(ManyakTheme.spacing.dense),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ManyakTheme.typography.titleMediumStrong,
            color = ManyakTheme.colors.text,
        )
        Text(
            text = count.toString(),
            style = ManyakTheme.typography.labelLarge,
            color = ManyakTheme.colors.textSubtle,
        )
    }
}

/** 요청 상태별 카드. 완성 중에는 아무 동작도 없고, 완료는 상세 진입을 연다. 실패한 요청은 초안 카드로 되돌아간다. */
@Composable
private fun CompletionRequestRow(
    request: CompletionRequestSummary,
    onOpenStory: (String) -> Unit,
) {
    when (request.status) {
        CompletionRequestStatus.PENDING ->
            CreationProgressCard(kind = CreationProgressCardKind.Completing, savedAt = request.createdAt)

        CompletionRequestStatus.COMPLETED ->
            CreationProgressCard(
                kind = CreationProgressCardKind.Completed(request.storyTitle.orEmpty()),
                onClick = request.storyId?.let { storyId -> { onOpenStory(storyId) } },
            )
    }
}

private const val DRAFT_KEY_PREFIX = "draft:"
private const val PROGRESS_SECTION_KEY = "section:progress"
private const val STORY_SECTION_KEY = "section:stories"
private const val COMPLETION_KEY_PREFIX = "completion:"

/** 복구 조회로 확정한 실패는 이유를 몰라 한 문구로 알리고, 원 응답에서 받은 수위 거절만 따로 알린다. */
@StringRes
internal fun CompletionFailure.messageRes(): Int =
    when (this) {
        CompletionFailure.FAILED -> StudioR.string.studio_completion_failed
        CompletionFailure.CONTENT_REJECTED -> CommonR.string.error_content_rejected
    }
