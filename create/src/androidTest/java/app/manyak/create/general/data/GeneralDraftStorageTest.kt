package app.manyak.create.general.data

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.manyak.common.domain.error.DomainError
import app.manyak.common.domain.error.DomainResult
import app.manyak.common.domain.user.UserProfileRepository
import app.manyak.common.entity.user.AccountStatus
import app.manyak.common.entity.user.UserProfile
import app.manyak.create.data.database.ManyakDatabase
import app.manyak.create.domain.StoryCreationRepository
import app.manyak.create.entity.ArtStyle
import app.manyak.create.entity.CompletedStory
import app.manyak.create.entity.CreationRequestSnapshot
import app.manyak.create.entity.GenreCatalog
import app.manyak.create.entity.StoryCompletionCommand
import app.manyak.create.entity.StoryTag
import app.manyak.create.entity.StoryTagCategory
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.entity.StorylineRating
import app.manyak.create.general.data.api.PutStoryDraftDto
import app.manyak.create.general.data.api.StoryDraftApi
import app.manyak.create.general.data.api.StoryDraftCardDto
import app.manyak.create.general.data.api.StoryDraftDetailDto
import app.manyak.create.general.data.api.StoryDraftSavedDto
import app.manyak.create.general.data.api.toDraftPayload
import app.manyak.create.general.data.database.GeneralDraftEntity
import app.manyak.create.general.data.database.GeneralDraftSnapshot
import app.manyak.create.general.entity.GeneralDraftOpen
import app.manyak.create.general.entity.GeneralDraftSave
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.util.UUID

class GeneralDraftStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val owner = "storage-test-${UUID.randomUUID()}"
    private val profile = StorageProfileRepository(owner)
    private val files = GeneralImageFiles(context, Dispatchers.IO)
    private val room = Room.inMemoryDatabaseBuilder(context, ManyakDatabase::class.java).build()
    private val server = FakeStoryDraftApi()
    private val catalog = FakeGenreCatalog()
    private val created = mutableListOf<File>()
    private val json = Json

    @After fun tearDown() {
        room.close()
        created.forEach { it.delete() }
        created.firstOrNull()?.parentFile?.delete()
    }

    @Test
    fun legacyDraftsMoveToTheServerUntilTheLimitAndOnlyMovedOnesLeaveTheDevice() =
        runBlocking {
            val store = store()
            val image = files.persist(owner, byteArrayOf(1, 2, 3), "image/png").also(created::add)
            legacy("conflict", GeneralStoryForm(title = "기기 안"), 30)
            legacy("moved", GeneralStoryForm(title = "옮길 초안", cover = image.asImage()), 20)
            legacy("full", GeneralStoryForm(title = "상한"), 10)
            legacy("other", GeneralStoryForm(title = "다른 계정"), 10, ownerId = "$owner-other")
            server.seed("conflict", GeneralStoryForm(title = "서버"))
            server.limit = 2

            store.refresh()

            assertEquals(listOf("conflict", "moved", "full"), server.puts.map { it.first })
            assertEquals(listOf("서버", "옮길 초안", "상한"), store.drafts.first().map { it.title })
            assertFalse(image.exists())
            assertTrue(room.generalDraftDao().find("$owner-other", "other") != null)
            assertTrue(store.open("full").let { (it as DomainResult.Success).value is GeneralDraftOpen.Local })
        }

    @Test
    fun savesChainVersionsResolveConflictsAndRecreateADraftDeletedElsewhere() =
        runBlocking {
            val store = store()
            server.seed("draft", GeneralStoryForm(title = "처음"))
            val opened = (store.open("draft") as DomainResult.Success).value as GeneralDraftOpen.Server
            assertEquals(1, opened.version)

            assertEquals(GeneralDraftSave.Saved(2), store.save("draft", GeneralStoryForm(title = "둘"), 1))
            assertEquals(GeneralDraftSave.Conflict(2), store.save("draft", GeneralStoryForm(title = "셋"), 1))
            assertEquals(GeneralDraftSave.Saved(3), store.save("draft", GeneralStoryForm(title = "셋"), 2))

            server.drafts.remove("draft")
            assertEquals(GeneralDraftSave.Saved(1), store.save("draft", GeneralStoryForm(title = "되살림"), 3))
            assertEquals(listOf(1, 1, 2, 3, null), server.puts.map { it.second })

            server.limit = 1
            assertEquals(GeneralDraftSave.LimitExceeded, store.save("new", GeneralStoryForm(title = "새"), null))

            assertTrue(store.markAccepted("draft", "submission"))
            assertEquals(GeneralDraftSave.Failed, store.save("draft", GeneralStoryForm(title = "늦은 저장"), 1))
        }

    @Test
    fun openingReadsWebGenresAndRefusesUnknownSchemas() =
        runBlocking {
            val store = store()
            assertEquals(DomainResult.Success(GeneralDraftOpen.NotFound), store.open("missing"))

            server.drafts["web"] = 1 to request(webGenrePayload())
            val form = ((store.open("web") as DomainResult.Success).value as GeneralDraftOpen.Server).form
            assertEquals(listOf("판타지"), form.genres)

            catalog.fails = true
            assertTrue(store.open("web") is DomainResult.Failure)

            server.drafts["future"] = 1 to request(JsonObject(emptyMap()), schemaVersion = 2)
            assertTrue(store.open("future") is DomainResult.Failure)
        }

    @Test
    fun listFailureKeepsTheLastCardsOfTheSameAccountOnly() =
        runBlocking {
            val store = store()
            server.seed("draft", GeneralStoryForm(title = "서버"))
            store.refresh()
            server.listFails = true
            store.refresh()
            assertEquals(listOf("draft"), store.drafts.first().map { it.draftId })

            profile.switchTo("$owner-other")
            store.refresh()
            assertTrue(store.drafts.first().isEmpty())
        }

    @Test
    fun deletingRemovesTheServerDraftAndTheDeviceCopy() =
        runBlocking {
            val store = store()
            server.seed("draft", GeneralStoryForm(title = "서버"))
            legacy("draft", GeneralStoryForm(title = "기기 안"), 10)

            assertTrue(store.delete("draft"))
            assertTrue(store.drafts.first().isEmpty())
            assertTrue(server.drafts.isEmpty())
        }

    private fun store() =
        GeneralDraftServerStore(server, room.generalDraftDao(), profile, catalog, json, Dispatchers.IO, files)

    private suspend fun legacy(
        draftId: String,
        form: GeneralStoryForm,
        createdAt: Long,
        ownerId: String = owner,
    ) {
        val snapshot = json.encodeToString(GeneralDraftSnapshot.serializer(), GeneralDraftSnapshot(form = form))
        room.generalDraftDao().save(GeneralDraftEntity(ownerId, draftId, snapshot, createdAt))
    }

    private fun File.asImage(): GeneralStoryImage =
        GeneralStoryImage("thumbnails/uploaded/$name", Uri.fromFile(this).toString(), absolutePath)
}

private fun request(
    payload: JsonObject,
    schemaVersion: Int = 1,
) = PutStoryDraftDto("GENERAL", "GENERAL_DRAFT", schemaVersion, payload, null, null, null, null)

private fun webGenrePayload(): JsonObject =
    buildJsonObject {
        putJsonObject("genres") {
            putJsonArray("selected") {
                add(
                    buildJsonObject {
                        put("kind", "tag")
                        put("id", JsonPrimitive(3))
                    },
                )
            }
            putJsonArray("customTags") {}
        }
    }

/** 서버의 저장 규칙(같은 내용 재전송, 기준 버전 비교, 상한)만 흉내 낸다. */
private class FakeStoryDraftApi : StoryDraftApi {
    val drafts = linkedMapOf<String, Pair<Int, PutStoryDraftDto>>()
    val puts = mutableListOf<Pair<String, Int?>>()
    var limit = 5
    var listFails = false

    fun seed(
        draftId: String,
        form: GeneralStoryForm,
    ) {
        drafts[draftId] = 1 to request(form.toDraftPayload()).copy(title = form.title)
    }

    override suspend fun list(): Response<List<StoryDraftCardDto>> =
        if (listFails) {
            Response.error(500, "{}".toResponseBody())
        } else {
            Response.success(
                drafts.entries.mapIndexed { index, (id, draft) ->
                    StoryDraftCardDto(
                        "DRAFT",
                        id,
                        "GENERAL",
                        draft.second.title,
                        null,
                        null,
                        "2026-10-16T00:00:0${9 - index}Z",
                    )
                },
            )
        }

    override suspend fun detail(draftId: String): Response<StoryDraftDetailDto> =
        drafts[draftId]?.let { (version, request) ->
            Response.success(
                StoryDraftDetailDto(draftId, request.mode, version, request.schemaVersion, request.payload),
            )
        } ?: Response.error(404, "{}".toResponseBody())

    override suspend fun put(
        draftId: String,
        request: PutStoryDraftDto,
    ): Response<StoryDraftSavedDto> {
        puts += draftId to request.baseVersion
        val current = drafts[draftId]
        val stored = request.copy(baseVersion = null)
        return when {
            current == null && request.baseVersion != null -> error(400, """{"code":"INVALID_INPUT"}""")
            current == null && drafts.size >= limit -> error(400, """{"code":"DRAFT_LIMIT_EXCEEDED"}""")
            current == null -> saved(draftId, 1, stored)
            current.second == stored -> Response.success(StoryDraftSavedDto(current.first))
            request.baseVersion != current.first ->
                error(
                    409,
                    """{"code":"DRAFT_VERSION_CONFLICT","details":[{"field":"version","message":"${current.first}"}]}""",
                )
            else -> saved(draftId, current.first + 1, stored)
        }
    }

    override suspend fun delete(draftId: String): Response<Unit> {
        drafts.remove(draftId)
        return Response.success(204, Unit)
    }

    private fun saved(
        draftId: String,
        version: Int,
        request: PutStoryDraftDto,
    ): Response<StoryDraftSavedDto> {
        drafts[draftId] = version to request
        return Response.success(StoryDraftSavedDto(version))
    }

    private fun error(
        code: Int,
        body: String,
    ): Response<StoryDraftSavedDto> = Response.error(code, body.toResponseBody())
}

private class FakeGenreCatalog : StoryCreationRepository {
    var fails = false

    override suspend fun genres(query: String): DomainResult<GenreCatalog> =
        if (fails) {
            DomainResult.Failure(DomainError.Network)
        } else {
            DomainResult.Success(GenreCatalog(listOf(StoryTag(3, "판타지", StoryTagCategory.GENRE)), emptyList()))
        }

    override suspend fun tags(): DomainResult<List<StoryTag>> = unused()

    override suspend fun artStyles(): DomainResult<List<ArtStyle>> = unused()

    override suspend fun generateStorylines(command: StorylineGenerationCommand): DomainResult<StorylineGeneration> =
        unused()

    override suspend fun completeStory(command: StoryCompletionCommand): DomainResult<CompletedStory> = unused()

    override suspend fun creationRequest(requestId: String): DomainResult<CreationRequestSnapshot> = unused()

    override suspend fun rateStoryline(
        storylineId: Long,
        rating: StorylineRating,
    ): DomainResult<Unit> = unused()

    override suspend fun clearStorylineRating(storylineId: Long): DomainResult<Unit> = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException()
}

private class StorageProfileRepository(
    ownerId: String,
) : UserProfileRepository {
    override val profile = MutableStateFlow<UserProfile?>(profileFor(ownerId))

    fun switchTo(ownerId: String) {
        profile.value = profileFor(ownerId)
    }

    override suspend fun refresh(): DomainResult<UserProfile> =
        profile.value?.let { DomainResult.Success(it) } ?: DomainResult.Failure(DomainError.Unauthorized)
}

private fun profileFor(ownerId: String): UserProfile =
    UserProfile(ownerId, "테스트", null, null, AccountStatus.ACTIVE, 0, false, emptyList())
