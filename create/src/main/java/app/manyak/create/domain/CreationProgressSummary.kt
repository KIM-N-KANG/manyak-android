package app.manyak.create.domain

import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.entity.CompletionOutcome
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.StoryCompletionRequest

fun PendingStoryCreation.resumePoint(): CreationResumePoint =
    when (this) {
        is PendingStoryCreation.KeywordDraft -> CreationResumePoint.KeywordStep

        is PendingStoryCreation.GeneratingStorylines -> CreationResumePoint.StorylineStep

        is PendingStoryCreation.Draft ->
            progress.selectedStorylineIndex
                ?.let { CreationResumePoint.AdditionalInfoStep(it) }
                ?: CreationResumePoint.StorylineStep
    }

fun PendingStoryCreation.toProgressSummary(): CreationProgressSummary =
    CreationProgressSummary(
        stage =
            when (this) {
                is PendingStoryCreation.KeywordDraft -> CreationStage.KEYWORD_DRAFT
                is PendingStoryCreation.GeneratingStorylines -> CreationStage.STORYLINE_GENERATION
                is PendingStoryCreation.Draft -> CreationStage.STORY_DRAFT
            },
        resumePoint = resumePoint(),
    )

fun StoryCompletionRequest.toSummary(): CompletionRequestSummary =
    CompletionRequestSummary(
        requestId = requestId,
        submittedAt = submittedAt,
        status =
            when (outcome) {
                CompletionOutcome.Pending -> CompletionRequestStatus.PENDING
                is CompletionOutcome.Completed -> CompletionRequestStatus.COMPLETED
                CompletionOutcome.Failed -> CompletionRequestStatus.FAILED
            },
        storyId = (outcome as? CompletionOutcome.Completed)?.story?.id,
        storyTitle = (outcome as? CompletionOutcome.Completed)?.story?.title,
    )
