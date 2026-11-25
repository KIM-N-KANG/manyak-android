package app.manyak.create.general.data

import app.manyak.create.general.data.api.draftGenreTagIds
import app.manyak.create.general.data.api.toDraftPayload
import app.manyak.create.general.data.api.toDraftRequest
import app.manyak.create.general.data.api.toGeneralStoryForm
import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralMainEvent
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneralDraftPayloadTest {
    @Test
    fun `웹이 저장한 v1 payload를 폼으로 읽는다`() {
        val form = webPayload.toGeneralStoryForm(mapOf("thumbnails/a.png" to "https://img/a.png"), mapOf(3L to "판타지"))

        assertEquals("노선도에 없는 역", form.title)
        assertEquals("세계관", form.world)
        assertEquals(7, form.descriptionRatio)
        assertEquals(GeneralCharacter(form.protagonist.id, name = "나", gender = GeneralGender.FEMALE), form.protagonist)
        assertEquals(GeneralStoryImage("thumbnails/a.png", "https://img/a.png"), form.cover)
        assertEquals("s1", form.supporting.single().id)
        assertEquals("소개", form.supporting.single().description)
        assertEquals(GeneralStoryImage("characters/b.png", ""), form.supporting.single().image)
        assertEquals(listOf("가", "나", "다"), form.startSettings.single().suggestedInputs)
        assertEquals(
            GeneralEnding("e1", "엔딩", "3", "조건", "에필로그"),
            form.startSettings
                .single()
                .endings
                .single(),
        )
        assertEquals(GeneralMainEvent("m1", "사건", "설명", "문장"), form.mainEvents.single())
        assertEquals(listOf("판타지", "직접 장르"), form.genres)
        assertEquals("PUBLIC", form.visibility)
        assertEquals(listOf(3L, 99L), webPayload.draftGenreTagIds())
    }

    @Test
    fun `빠졌거나 타입이 다른 필드는 새 폼의 기본값으로 채운다`() {
        val form =
            json
                .parseToJsonElement(
                    """{"texts":{"title":1},"descriptionRatio":"x","supporting":[],
                    "startSettings":[{"name":"시작"}],"mainEvents":"x",
                    "genres":{"selected":[{"kind":"tag","id":99}]},"visibility":"ALL","cover":{"key":"x"}}""",
                ).jsonObject
                .toGeneralStoryForm(emptyMap(), emptyMap())
        val empty = GeneralStoryForm()

        assertEquals("", form.title)
        assertEquals(empty.descriptionRatio, form.descriptionRatio)
        assertEquals(1, form.supporting.size)
        assertEquals("시작", form.startSettings.single().name)
        assertEquals(listOf("", "", ""), form.startSettings.single().suggestedInputs)
        assertEquals(emptyList<GeneralMainEvent>(), form.mainEvents)
        assertEquals(emptyList<String>(), form.genres)
        assertEquals("PRIVATE", form.visibility)
        assertNull(form.cover)
    }

    @Test
    fun `Android 폼은 이미지 객체 키만 보내고 같은 폼으로 돌아온다`() {
        val form =
            GeneralStoryForm(
                title = " 제목 ",
                oneLineIntro = "  ",
                world = "세계관",
                protagonist = GeneralCharacter(name = "나", gender = GeneralGender.MALE, feature = "특징"),
                supporting =
                    listOf(
                        GeneralCharacter(
                            id = "s1",
                            serverId = "server",
                            name = "동료",
                            image = GeneralStoryImage("characters/b.png", "file:///b.png", "/b.png"),
                        ),
                    ),
                startSettings =
                    listOf(
                        GeneralStartSetting(id = "st", name = "시작", endings = listOf(GeneralEnding("e"))),
                    ),
                mainEvents = listOf(GeneralMainEvent("m", "사건")),
                genres = listOf("판타지", "로맨스"),
                cover = GeneralStoryImage("thumbnails/a.png", "file:///a.png", "/a.png"),
            )
        val request = form.toDraftRequest(baseVersion = 4)
        val payload = form.toDraftPayload()
        val restored = payload.toGeneralStoryForm(mapOf("thumbnails/a.png" to "https://img/a.png"), emptyMap())

        assertEquals("제목", request.title)
        assertNull(request.oneLineIntro)
        assertEquals("thumbnails/a.png", request.thumbnailObjectKey)
        assertEquals(4, request.baseVersion)
        assertEquals(
            """{"objectKey":"characters/b.png"}""",
            payload["supporting"]!!.jsonArray[0].jsonObject["image"].toString(),
        )
        assertEquals(
            form.copy(
                protagonist = form.protagonist.copy(id = restored.protagonist.id),
                supporting =
                    listOf(
                        form.supporting.single().copy(
                            serverId = null,
                            image = GeneralStoryImage("characters/b.png", ""),
                        ),
                    ),
                cover = GeneralStoryImage("thumbnails/a.png", "https://img/a.png"),
            ),
            restored,
        )
        assertEquals(emptyList<Long>(), payload.draftGenreTagIds())
    }

    private val json = Json

    /** 웹 `toGeneralDraftRequest`가 만드는 payload와 같은 모양이다. */
    private val webPayload: JsonObject =
        json
            .parseToJsonElement(
                """
                {
                  "texts": {"title": "노선도에 없는 역", "oneLineIntro": "소개", "world": "세계관", "progression": "전개"},
                  "cover": {"objectKey": "thumbnails/a.png"},
                  "descriptionRatio": 7,
                  "protagonist": {"name": "나", "gender": "FEMALE", "feature": ""},
                  "supporting": [
                    {"id": "s1", "name": "동료", "gender": null, "description": "소개", "feature": "",
                     "image": {"objectKey": "characters/b.png"}}
                  ],
                  "startSettings": [
                    {"id": "st1", "name": "시작", "prologue": "", "situation": "", "suggestedInputs": ["가", "나", "다"],
                     "endings": [{"id": "e1", "name": "엔딩", "minTurns": "3", "condition": "조건", "epilogue": "에필로그"}]}
                  ],
                  "mainEvents": [{"id": "m1", "name": "사건", "description": "설명", "keySentence": "문장"}],
                  "genres": {
                    "selected": [{"kind": "tag", "id": 3}, {"kind": "custom", "id": "c1"}, {"kind": "tag", "id": 99}],
                    "customTags": [{"id": "c1", "name": "직접 장르"}],
                    "addedTagIds": [3]
                  },
                  "description": "",
                  "visibility": "PUBLIC"
                }
                """.trimIndent(),
            ).jsonObject
}
