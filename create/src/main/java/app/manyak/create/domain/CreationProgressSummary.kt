package app.manyak.create.domain

import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.entity.PendingStoryCreation

fun PendingStoryCreation.resumePoint(): CreationResumePoint =
    when (this) {
        is PendingStoryCreation.KeywordDraft -> CreationResumePoint.KeywordStep

        is PendingStoryCreation.GeneratingStorylines -> CreationResumePoint.StorylineStep

        is PendingStoryCreation.CompletingStory ->
            CreationResumePoint.AdditionalInfoStep(progress.selectedStorylineIndex ?: 0)

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
                is PendingStoryCreation.CompletingStory -> CreationStage.STORY_COMPLETION
                is PendingStoryCreation.Draft -> CreationStage.STORY_DRAFT
            },
        resumePoint = resumePoint(),
    )
