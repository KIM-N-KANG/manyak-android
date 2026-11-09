package app.manyak.create.general.data

import android.database.sqlite.SQLiteException
import app.manyak.common.domain.story.CreationProgressAccess
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.data.completion.StoryCompletionExecutor
import app.manyak.create.domain.PendingStoryCreationStore
import app.manyak.create.domain.SimpleDraftStore
import app.manyak.create.domain.StoryCompletionRequestStore
import app.manyak.create.domain.hiddenServerDraft
import app.manyak.create.domain.mergeSimpleDrafts
import app.manyak.create.general.domain.GeneralDraftStore
import app.manyak.create.general.domain.GeneralStoryRepository
import app.manyak.create.general.entity.GeneralStoredDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CreationProgressRepository
    @Inject
    constructor(
        private val simple: StoryCompletionExecutor,
        private val simpleLocal: PendingStoryCreationStore,
        private val simpleServer: SimpleDraftStore,
        completionRequests: StoryCompletionRequestStore,
        private val general: GeneralDraftStore,
        generalStories: GeneralStoryRepository,
    ) : CreationProgressAccess by simple {
        override val submissionChanges = generalStories.submissionChanges

        override val drafts =
            combine(
                simpleLocal.drafts,
                completionRequests.requests,
                simpleServer.drafts,
                general.drafts,
            ) { local, completions, server, generalDrafts ->
                (mergeSimpleDrafts(local, completions, server) + generalDrafts.map { it.toSummary() })
                    .sortedWith(compareByDescending<CreationProgressSummary> { it.createdAt })
            }

        /** 서버 목록은 두 제작 방식의 초안을 함께 돌려줘 일반 제작 새로고침 한 번이 간편 제작 카드도 갱신한다. */
        override suspend fun refreshDrafts() = general.refresh()

        /** 기기 안 간편 제작 카드를 지우면 그 카드가 가려 한 장으로 보이던 같은 제작의 서버 초안도 지운다. */
        override suspend fun discard(
            draftId: String,
            onServer: Boolean,
        ): Boolean =
            try {
                val local =
                    simpleLocal.drafts
                        .first()
                        .firstOrNull { it.draftId == draftId }
                        .takeUnless { onServer }
                val server = simpleServer.drafts.first()
                when {
                    local != null ->
                        (local.hiddenServerDraft(server) == null || simpleServer.delete(draftId)) &&
                            simple.discard(draftId)

                    server.any { it.draftId == draftId } -> simpleServer.delete(draftId)

                    general.drafts.first().any { it.draftId == draftId } -> general.delete(draftId)

                    else -> simple.discard(draftId)
                }
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
        title = title,
        oneLineIntro = oneLineIntro,
        thumbnailUrl = thumbnailUrl,
    )
