package app.manyak.create.domain

import app.manyak.common.entity.story.CompletionRequestStatus
import app.manyak.common.entity.story.CompletionRequestSummary
import app.manyak.common.entity.story.CreationProgressSummary
import app.manyak.common.entity.story.CreationResumePoint
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.entity.CompletionOutcome
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.ServerDraftLink
import app.manyak.create.entity.SimpleServerDraft
import app.manyak.create.entity.StoredCreationDraft
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

fun StoredCreationDraft.toProgressSummary(): CreationProgressSummary =
    CreationProgressSummary(
        draftId = draftId,
        stage =
            when (record) {
                is PendingStoryCreation.KeywordDraft -> CreationStage.KEYWORD_DRAFT
                is PendingStoryCreation.GeneratingStorylines -> CreationStage.STORYLINE_GENERATION
                is PendingStoryCreation.Draft -> CreationStage.STORY_DRAFT
            },
        resumePoint = record.resumePoint(),
        createdAt = createdAt,
    )

/** 이전 버전이 실패로 남긴 요청은 다음 새로고침이 초안으로 되돌리므로 카드로 그리지 않는다. */
fun StoryCompletionRequest.toSummary(): CompletionRequestSummary? =
    CompletionRequestSummary(
        requestId = requestId,
        submittedAt = submittedAt,
        status =
            when (outcome) {
                CompletionOutcome.Pending -> CompletionRequestStatus.PENDING
                is CompletionOutcome.Completed -> CompletionRequestStatus.COMPLETED
                CompletionOutcome.Failed -> return null
            },
        storyId = (outcome as? CompletionOutcome.Completed)?.story?.id,
        storyTitle = (outcome as? CompletionOutcome.Completed)?.story?.title,
        createdAt = createdAt,
    )

/** 기기 안 진행 기록이 가리키는 서버 초안. 키워드 초안은 서버 세션에서 기기에 남지 않아 연결이 없다. */
val PendingStoryCreation.serverDraftLink: ServerDraftLink?
    get() =
        when (this) {
            is PendingStoryCreation.GeneratingStorylines -> serverDraft
            is PendingStoryCreation.Draft -> serverDraft
            is PendingStoryCreation.KeywordDraft -> null
        }

/** 이 기기 안 카드가 가려 한 장으로 보이는 같은 제작의 서버 초안. 기기 안 카드를 지울 때 함께 지운다. */
fun StoredCreationDraft.hiddenServerDraft(server: List<SimpleServerDraft>): SimpleServerDraft? =
    record.serverDraftLink?.let { link -> server.firstOrNull { it.draftId == draftId && link.hides(it.version) } }

/**
 * 기기 안 간편 제작 초안과 서버 초안을 한 묶음으로 합친다. 같은 제작의 기기 안 진행 기록(생성 중, 생성 결과)이나 완성 요청이
 * 가리는 서버 카드는 빼고, 기록을 남긴 뒤 다른 곳에서 저장한 서버 초안은 따로 보인다.
 */
fun mergeSimpleDrafts(
    local: List<StoredCreationDraft>,
    completions: List<StoryCompletionRequest>,
    server: List<SimpleServerDraft>,
): List<CreationProgressSummary> {
    val links =
        local.mapNotNull { draft -> draft.record.serverDraftLink?.let { draft.draftId to it } } +
            completions.mapNotNull { request ->
                request.serverDraft?.let { link -> request.command.draftId?.let { it to link } }
            }
    val visible =
        server.filterNot { card ->
            links.any { (id, link) -> id == card.draftId && link.hides(card.version) }
        }
    return local.map { it.toProgressSummary() } + visible.map { it.toProgressSummary() }
}

private fun SimpleServerDraft.toProgressSummary(): CreationProgressSummary =
    CreationProgressSummary(
        draftId = draftId,
        stage = stage,
        resumePoint =
            if (stage ==
                CreationStage.KEYWORD_DRAFT
            ) {
                CreationResumePoint.KeywordStep
            } else {
                CreationResumePoint.StorylineStep
            },
        createdAt = createdAt,
        oneLineIntro = oneLineIntro,
        onServer = true,
    )

/**
 * 완성에 실패한 요청을 제출한 입력 그대로 추가 정보 단계 편집 초안으로 되돌린다. 제출한 명령과 화풍, 공개 범위를 남겨 같은 입력으로
 * 다시 완성하면 같은 requestId 를 쓰고, 서버 초안 연결은 그대로 둔다.
 */
fun StoryCompletionRequest.toReturnedDraft(): PendingStoryCreation.Draft =
    PendingStoryCreation.Draft(
        generationCommand = generationCommand,
        generation = generation,
        progress =
            progress.copy(
                selectedStorylineIndex =
                    progress.selectedStorylineIndex
                        ?: generation.storylines.indexOfFirst { it.id == command.storylineId }.takeIf { it >= 0 },
                artStyle = command.artStyle ?: progress.artStyle,
                visibility = command.visibility,
            ),
        lastCompletionCommand = command,
        serverDraft = serverDraft,
    )
