package app.manyak.create.data

import app.manyak.common.entity.story.StoryVisibility
import app.manyak.create.data.api.StoryDraftDetailDto
import app.manyak.create.data.api.toSimpleDraftOpen
import app.manyak.create.data.api.toSimpleDraftRequest
import app.manyak.create.entity.CharacterGender
import app.manyak.create.entity.CreationProgress
import app.manyak.create.entity.KeywordCharacterSnapshot
import app.manyak.create.entity.KeywordCustomTagSnapshot
import app.manyak.create.entity.KeywordDraftSnapshot
import app.manyak.create.entity.PendingStoryCreation
import app.manyak.create.entity.SimpleDraftOpen
import app.manyak.create.entity.StoryCharacterInput
import app.manyak.create.entity.Storyline
import app.manyak.create.entity.StorylineGeneration
import app.manyak.create.entity.StorylineGenerationCommand
import app.manyak.create.entity.StorylineRecommendedInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SimpleDraftPayloadTest {
    @Test
    fun `키워드 초안은 웹과 같은 키워드 입력 전체를 KEYWORD_DRAFT로 보낸다`() {
        val request = PendingStoryCreation.KeywordDraft(keywordSnapshot).toSimpleDraftRequest(baseVersion = 2)!!

        assertEquals("SIMPLE", request.mode)
        assertEquals("KEYWORD_DRAFT", request.stage)
        assertEquals(1, request.schemaVersion)
        assertEquals(2, request.baseVersion)
        assertNull(request.title)
        assertNull(request.oneLineIntro)
        assertNull(request.thumbnailObjectKey)
        assertNull(request.generationRequestId)
        assertEquals(
            json.parseToJsonElement(
                """{"selectedGenreTagIds":[3],"addedGenreTagIds":[99],
                "customGenreTags":[{"name":"옛 장르","selected":true}],
                "protagonist":{"name":"나","gender":"FEMALE","selectedTagIds":[11],
                "customTags":[{"name":"용감함","selected":false}]},
                "supportingCharacters":[{"name":"","gender":null,"selectedTagIds":[],"customTags":[]}]}""",
            ),
            request.payload,
        )
    }

    @Test
    fun `추가 정보 단계의 스토리라인 초안은 생성 요청에 잇고 결과 원문 없이 고른 스토리라인을 가리킨다`() {
        val request = storyDraft(selectedIndex = 1).toSimpleDraftRequest(baseVersion = null)!!

        assertEquals("STORY_DRAFT", request.stage)
        assertNull(request.baseVersion)
        assertEquals("gen-1", request.generationRequestId)
        assertEquals("둘째", request.oneLineIntro)
        assertEquals(
            json.parseToJsonElement(
                """{"step":"additional-info",
                "generationRequest":{"requestId":"gen-1","genreTagIds":[3],"customGenreTags":[],
                "protagonist":{"name":"나","gender":"FEMALE","featureTagIds":[11],"customTags":["용감함"]},
                "supportingCharacters":[{"name":null,"gender":null,"featureTagIds":[],"customTags":[]}],
                "parentCreationId":null,"isRegenerated":false},
                "activeStorylineIndex":1,"selectedStorylineId":102,"additionalInfos":["배경은 서울",""],
                "selectedRecommendations":["폐허"],"artStyle":"ANIME","visibility":"PRIVATE","createdStoryId":null,
                "completionRequest":null}""",
            ),
            request.payload,
        )
    }

    @Test
    fun `스토리라인 선택 단계는 카드 설명과 고른 스토리라인이 없다`() {
        val request = storyDraft(selectedIndex = null).toSimpleDraftRequest(baseVersion = 3)!!

        assertNull(request.oneLineIntro)
        assertEquals("storyline-select", request.payload["step"].toString().trim('"'))
        assertEquals("null", request.payload["selectedStorylineId"].toString())
    }

    @Test
    fun `생성 요청을 모르는 이전 기록과 진행 중 기록은 서버에 보내지 않는다`() {
        assertNull(storyDraft(selectedIndex = null).copy(generationCommand = null).toSimpleDraftRequest(null))
        assertNull(PendingStoryCreation.GeneratingStorylines(command).toSimpleDraftRequest(null))
    }

    @Test
    fun `웹이 null로 보낸 선택 필드는 없음으로 읽는다`() {
        val opened =
            detail(
                stage = "KEYWORD_DRAFT",
                payload =
                    """{"selectedGenreTagIds":[3],"addedGenreTagIds":null,"customGenreTags":[],
                    "protagonist":{"name":"","gender":null,"selectedTagIds":[],"customTags":[]},
                    "supportingCharacters":[]}""",
            ).toSimpleDraftOpen(json)

        assertEquals(
            SimpleDraftOpen.Found(
                PendingStoryCreation.KeywordDraft(
                    KeywordDraftSnapshot(
                        selectedGenreTagIds = listOf(3),
                        customGenreTags = emptyList(),
                        protagonist = KeywordCharacterSnapshot("", null, emptyList(), emptyList()),
                        supportingCharacters = emptyList(),
                        addedGenreTagIds = emptyList(),
                    ),
                ),
                version = 4,
                createdAt = Instant.parse("2026-10-17T01:00:00Z").toEpochMilli(),
            ),
            opened,
        )
    }

    @Test
    fun `웹 스토리라인 초안은 상세의 생성 결과와 합쳐 추가 정보 단계로 연다`() {
        val opened =
            detail(
                stage = "STORY_DRAFT",
                payload = WEB_STORY_PAYLOAD,
                generation = COMPLETED_GENERATION,
            ).toSimpleDraftOpen(json)

        val record = (opened as SimpleDraftOpen.Found).record as PendingStoryCreation.Draft
        assertEquals(4, opened.version)
        assertEquals("gen-1", record.generationCommand?.requestId)
        assertEquals(emptyList<String>(), record.generationCommand?.customGenreTags)
        assertEquals(generation, record.generation)
        assertEquals(
            CreationProgress(
                selectedStorylineIndex = 1,
                activeStorylineIndex = 1,
                additionalInfoInputs = listOf("배경"),
                selectedRecommendations = emptyList(),
                artStyle = null,
            ),
            record.progress,
        )
        // 웹은 공개 범위를 싣지 않아 스위치 기본값(공개)으로 연다.
        assertEquals(StoryVisibility.PUBLIC, record.progress.visibility)
    }

    @Test
    fun `고른 스토리라인을 결과에서 찾지 못하면 스토리라인 선택 단계로 연다`() {
        val opened =
            detail(
                stage = "STORY_DRAFT",
                payload = WEB_STORY_PAYLOAD.replace("\"selectedStorylineId\":102", "\"selectedStorylineId\":999"),
                generation = COMPLETED_GENERATION,
            ).toSimpleDraftOpen(json)

        val record = (opened as SimpleDraftOpen.Found).record as PendingStoryCreation.Draft
        assertNull(record.progress.selectedStorylineIndex)
    }

    @Test
    fun `생성 결과를 받을 수 없는 스토리라인 초안은 찾지 못한 초안으로 버전과 함께 돌려준다`() {
        val failed = """{"stage":"STORYLINE_GENERATION","status":"FAILED","result":null}"""

        assertEquals(
            SimpleDraftOpen.NotFound(4),
            detail("STORY_DRAFT", WEB_STORY_PAYLOAD, failed).toSimpleDraftOpen(json),
        )
        assertEquals(SimpleDraftOpen.NotFound(4), detail("STORY_DRAFT", WEB_STORY_PAYLOAD).toSimpleDraftOpen(json))
    }

    @Test
    fun `다른 스키마 버전, 모르는 단계, 키워드 형태를 읽을 수 없으면 실패다`() {
        assertNull(
            detail("STORY_DRAFT", WEB_STORY_PAYLOAD, COMPLETED_GENERATION, schemaVersion = 2).toSimpleDraftOpen(json),
        )
        assertNull(
            detail("STORY_DRAFT", WEB_STORY_PAYLOAD.replace("additional-info", "complete"), COMPLETED_GENERATION)
                .toSimpleDraftOpen(json),
        )
        assertNull(detail("KEYWORD_DRAFT", """{"selectedGenreTagIds":"x"}""").toSimpleDraftOpen(json))
        assertNull(detail("GENERAL_DRAFT", "{}", mode = "GENERAL").toSimpleDraftOpen(json))
    }

    @Test
    fun `Android 스토리라인 초안은 읽고 다시 써도 같은 기록이다`() {
        val draft = storyDraft(selectedIndex = 1)
        val request = draft.toSimpleDraftRequest(baseVersion = 1)!!
        val opened =
            StoryDraftDetailDto(
                draftId = "d1",
                mode = "SIMPLE",
                stage = request.stage,
                version = 2,
                schemaVersion = 1,
                payload = request.payload,
                generation = json.decodeFromString(COMPLETED_GENERATION),
            ).toSimpleDraftOpen(json)

        assertEquals(SimpleDraftOpen.Found(draft, 2), opened)
    }

    private fun detail(
        stage: String,
        payload: String,
        generation: String? = null,
        schemaVersion: Int = 1,
        mode: String = "SIMPLE",
    ): StoryDraftDetailDto =
        json.decodeFromString(
            """{"draftId":"d1","mode":"$mode","stage":"$stage","title":null,"oneLineIntro":null,"thumbnailUrl":null,
            "version":4,"createdAt":"2026-10-17T01:00:00Z","updatedAt":"2026-10-17T01:00:00Z",
            "schemaVersion":$schemaVersion,"payload":$payload,"generationRequestId":null,"imageUrls":{},
            "generation":${generation ?: "null"}}""",
        )

    private fun storyDraft(selectedIndex: Int?): PendingStoryCreation.Draft =
        PendingStoryCreation.Draft(
            generationCommand = command,
            generation = generation,
            progress =
                CreationProgress(
                    selectedStorylineIndex = selectedIndex,
                    activeStorylineIndex = 1,
                    additionalInfoInputs = if (selectedIndex == null) emptyList() else listOf("배경은 서울", ""),
                    selectedRecommendations = if (selectedIndex == null) emptyList() else listOf("폐허"),
                    artStyle = if (selectedIndex == null) null else "ANIME",
                    visibility = if (selectedIndex == null) StoryVisibility.PUBLIC else StoryVisibility.PRIVATE,
                ),
        )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        val keywordSnapshot =
            KeywordDraftSnapshot(
                selectedGenreTagIds = listOf(3),
                customGenreTags = listOf(KeywordCustomTagSnapshot("옛 장르", selected = true)),
                protagonist =
                    KeywordCharacterSnapshot(
                        name = "나",
                        gender = CharacterGender.FEMALE,
                        selectedTagIds = listOf(11),
                        customTags = listOf(KeywordCustomTagSnapshot("용감함", selected = false)),
                    ),
                supportingCharacters = listOf(KeywordCharacterSnapshot("", null, emptyList(), emptyList())),
                addedGenreTagIds = listOf(99),
            )

        val command =
            StorylineGenerationCommand(
                requestId = "gen-1",
                genreTagIds = listOf(3),
                customGenreTags = emptyList(),
                protagonist = StoryCharacterInput("나", CharacterGender.FEMALE, listOf(11), listOf("용감함")),
                supportingCharacters = listOf(StoryCharacterInput(null, null, emptyList(), emptyList())),
                parentCreationId = null,
                isRegenerated = false,
            )

        val generation =
            StorylineGeneration(
                simpleCreationId = 7,
                storylines =
                    listOf(
                        Storyline(101, "첫째", emptyList()),
                        Storyline(102, "둘째", listOf(StorylineRecommendedInfo(1, "폐허"))),
                    ),
            )

        const val COMPLETED_GENERATION =
            """{"stage":"STORYLINE_GENERATION","status":"COMPLETED","result":{"simpleCreationId":7,"storylines":[
            {"id":101,"storyline":"첫째","recommendedInfos":[]},
            {"id":102,"storyline":"둘째","recommendedInfos":[{"id":1,"text":"폐허"}]}]}}"""

        /** 웹은 새 생성 요청에 직접 입력 장르와 재생성 플래그를 싣지 않는다. */
        const val WEB_STORY_PAYLOAD =
            """{"step":"additional-info","generationRequest":{"requestId":"gen-1","genreTagIds":[3],
            "protagonist":{"name":null,"gender":null,"featureTagIds":[11],"customTags":[]},
            "supportingCharacters":[],"parentCreationId":null},
            "activeStorylineIndex":1,"selectedStorylineId":102,"additionalInfos":["배경"],
            "selectedRecommendations":[],"artStyle":null,"createdStoryId":null,"completionRequest":null}"""
    }
}
