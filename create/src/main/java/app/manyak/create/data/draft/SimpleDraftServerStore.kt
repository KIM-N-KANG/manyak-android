package app.manyak.create.data.draft

import app.manyak.common.data.time.toEpochMillisOrNull
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.story.CreationStage
import app.manyak.create.data.api.SIMPLE_KEYWORD_STAGE
import app.manyak.create.data.api.SIMPLE_MODE
import app.manyak.create.data.api.SIMPLE_STORY_STAGE
import app.manyak.create.data.api.StoryDraftCardDto
import app.manyak.create.data.api.toSimpleDraftOpen
import app.manyak.create.data.api.toSimpleDraftRequest
import app.manyak.create.domain.SimpleDraftStore
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.SimpleServerDraft
import app.manyak.create.entity.StoryDraftSave
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleDraftServerStore
    @Inject
    constructor(
        private val remote: StoryDraftRemote,
        private val profileRepository: UserProfileRepository,
        private val json: Json,
    ) : SimpleDraftStore {
        override val drafts: Flow<List<SimpleServerDraft>> =
            profileRepository.profile
                .map { it?.id }
                .flatMapLatest { ownerId ->
                    if (ownerId == null) {
                        flowOf(emptyList())
                    } else {
                        remote.cards(ownerId).map { cards -> cards.mapNotNull { it.toSimpleDraft() } }
                    }
                }

        override suspend fun open(draftId: String): DomainResult<SimpleDraftOpen> =
            when (val result = remote.detail(draftId)) {
                is DomainResult.Failure ->
                    if (result.error.isNotFound()) DomainResult.Success(SimpleDraftOpen.NotFound()) else result

                is DomainResult.Success ->
                    result.value.toSimpleDraftOpen(json)?.let { DomainResult.Success(it) }
                        ?: DomainResult.Failure(DomainError.Serialization)
            }

        override suspend fun save(
            draftId: String,
            record: PendingStoryCreation,
            baseVersion: Int?,
        ): StoryDraftSave {
            val request = record.toSimpleDraftRequest(baseVersion) ?: return StoryDraftSave.Failed
            return remote.save(draftId, baseVersion) { request.copy(baseVersion = it) }
        }

        override suspend fun delete(draftId: String): Boolean = remote.delete(draftId)
    }

private fun StoryDraftCardDto.toSimpleDraft(): SimpleServerDraft? {
    val id = draftId?.takeIf { mode == SIMPLE_MODE } ?: return null
    val stage =
        when (stage) {
            SIMPLE_KEYWORD_STAGE -> CreationStage.KEYWORD_DRAFT
            SIMPLE_STORY_STAGE -> CreationStage.STORY_DRAFT
            else -> return null
        }
    return SimpleServerDraft(id, stage, oneLineIntro, version ?: return null, createdAt?.toEpochMillisOrNull())
}
