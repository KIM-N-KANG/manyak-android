package app.manyak.create.testing

import app.manyak.common.domain.error.DomainResult
import app.manyak.create.domain.SimpleDraftStore
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.SimpleServerDraft
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.StoryDraftSave
import app.manyak.create.entity.StorylineGenerationCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal fun sampleGenerationCommand(requestId: String = "gen-sample"): StorylineGenerationCommand =
    StorylineGenerationCommand(
        requestId = requestId,
        genreTagIds = listOf(1),
        customGenreTags = emptyList(),
        protagonist = StoryCharacterInput(null, null, emptyList(), emptyList()),
        supportingCharacters = emptyList(),
        parentCreationId = null,
        isRegenerated = false,
    )

/** 서버 초안 API 규칙(버전 증가, 기준 버전 불일치 충돌)을 흉내 낸다. [gate]가 있으면 저장 응답을 그때까지 붙잡는다. */
internal class FakeSimpleDraftStore : SimpleDraftStore {
    val stored = mutableMapOf<String, Pair<PendingStoryCreation, Int>>()
    val saves = mutableListOf<Save>()
    val deleted = mutableListOf<String>()
    val queuedResults = ArrayDeque<StoryDraftSave>()
    var openResult: DomainResult<SimpleDraftOpen>? = null
    var openCount = 0
    var gate: CompletableDeferred<Unit>? = null
    private val cards = MutableStateFlow<List<SimpleServerDraft>>(emptyList())

    data class Save(
        val draftId: String,
        val record: PendingStoryCreation,
        val baseVersion: Int?,
    )

    override val drafts: Flow<List<SimpleServerDraft>> = cards

    override suspend fun open(draftId: String): DomainResult<SimpleDraftOpen> {
        openCount++
        return openResult
            ?: DomainResult.Success(
                stored[draftId]?.let { (record, version) -> SimpleDraftOpen.Found(record, version) }
                    ?: SimpleDraftOpen.NotFound(),
            )
    }

    override suspend fun save(
        draftId: String,
        record: PendingStoryCreation,
        baseVersion: Int?,
    ): StoryDraftSave {
        saves += Save(draftId, record, baseVersion)
        gate?.await()
        queuedResults.removeFirstOrNull()?.let { return it }
        val current = stored[draftId]?.second
        if (current != baseVersion) return StoryDraftSave.Conflict(current ?: 0)
        val version = (current ?: 0) + 1
        stored[draftId] = record to version
        return StoryDraftSave.Saved(version)
    }

    override suspend fun delete(draftId: String): Boolean {
        deleted += draftId
        stored.remove(draftId)
        return true
    }
}
