package app.manyak.create.general.data

import android.database.sqlite.SQLiteException
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.data.completion.StoryCompletionExecutor
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.entity.GeneralStoredDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class CreationProgressRepository
    @Inject
    constructor(
        private val simple: StoryCompletionExecutor,
        private val general: GeneralDraftStore,
        generalStories: GeneralStoryRepository,
    ) : CreationProgressAccess by simple {
        override val submissionChanges = generalStories.submissionChanges

        override val drafts =
            combine(simple.drafts, general.drafts) { simpleDrafts, generalDrafts ->
                (simpleDrafts + generalDrafts.map { it.toSummary() })
                    .sortedWith(compareByDescending<CreationProgressSummary> { it.createdAt })
            }

        override suspend fun discard(draftId: String): Boolean =
            try {
                if (general.read(draftId) != null) general.delete(draftId) else simple.discard(draftId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SQLiteException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
    }

private fun GeneralStoredDraft.toSummary(): CreationProgressSummary =
    CreationProgressSummary(
        draftId = draftId,
        stage = CreationStage.GENERAL_DRAFT,
        resumePoint = CreationResumePoint.GeneralStep,
        createdAt = createdAt,
        title = form.title,
        oneLineIntro = form.oneLineIntro,
        thumbnailUrl = form.cover?.previewUrl,
    )
