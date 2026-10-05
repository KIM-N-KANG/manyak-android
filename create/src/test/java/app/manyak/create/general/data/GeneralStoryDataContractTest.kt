package app.manyak.create.general.data

import app.manyak.create.general.data.api.GeneralStoryEditDto
import app.manyak.create.general.data.api.GeneralStoryPatchDto
import app.manyak.create.general.data.api.GeneralSubmissionDto
import app.manyak.create.general.data.api.SingleSubmissionBody
import app.manyak.create.general.data.api.toEditor
import app.manyak.create.general.data.api.toRequest
import app.manyak.create.general.entity.GeneralCharacterInput
import app.manyak.create.general.entity.GeneralImageInput
import app.manyak.create.general.entity.GeneralStartInput
import app.manyak.create.general.entity.GeneralStoryPatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralStoryDataContractTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `공개 범위만 바꾸는 PATCH는 나머지 필드를 전송하지 않는다`() {
        val request = GeneralStoryPatch(visibility = "PUBLIC").toRequest()
        val body = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals(setOf("visibility"), body.keys)
    }

    @Test
    fun `소개와 인물을 지우는 요청은 빈 값도 보존한다`() {
        val body =
            json.parseToJsonElement(
                json.encodeToString(GeneralStoryPatchDto(description = "", characters = emptyList())),
            )
        assertEquals("", json.decodeFromJsonElement(GeneralStoryPatchDto.serializer(), body).description)
        assertEquals(emptyList<Any>(), json.decodeFromJsonElement(GeneralStoryPatchDto.serializer(), body).characters)
    }

    @Test
    fun `수정 PATCH는 기존 인물과 이미지 식별자를 유지하고 새 이미지는 객체 키로 보낸다`() {
        val patch =
            GeneralStoryPatch(
                characters =
                    listOf(
                        GeneralCharacterInput(
                            id = "character-id",
                            name = "변경한 이름",
                            description = "",
                            images =
                                listOf(
                                    GeneralImageInput(id = "existing-image"),
                                    GeneralImageInput(objectKey = "new-image-key", imageName = "변경한 이름_기본"),
                                ),
                        ),
                    ),
                startSettings = listOf(GeneralStartInput("start-id", "시작", "프롤로그", "상황", listOf("1", "2", "3"))),
            )
        val body = json.parseToJsonElement(json.encodeToString(patch.toRequest())).jsonObject
        val character =
            body
                .getValue("characters")
                .jsonArray
                .single()
                .jsonObject
        val images = character.getValue("images").jsonArray
        assertEquals("character-id", character.getValue("id").jsonPrimitive.content)
        assertEquals("", character.getValue("description").jsonPrimitive.content)
        assertEquals(setOf("id"), images[0].jsonObject.keys)
        assertEquals(setOf("objectKey", "imageName"), images[1].jsonObject.keys)
        val setting =
            body
                .getValue("startSettings")
                .jsonArray
                .single()
                .jsonObject
        assertEquals("start-id", setting.getValue("id").jsonPrimitive.content)
        assertFalse("thumbnailObjectKey" in body)
    }

    @Test
    fun `수정 폼은 서버가 합친 미승인 입력과 nullable 기존 필드를 그대로 읽는다`() {
        val editor =
            json
                .decodeFromString<GeneralStoryEditDto>(
                    """{
                "title":"수정 제출본 제목", "oneLineIntro":null, "visibility":"PRIVATE",
                "storySettings":{"worldSetting":null,"ruleSetting":"제출한 규칙"},
                "startSettings":[{"id":"start","name":"시작","prologue":null,"startSituation":null}],
                "thumbnailObjectKey":"pending-cover", "thumbnailUrl":"https://images/pending.png",
                "submission":{"submissionId":"update-submission","status":"REJECTED","issues":[]}
            }""",
                ).toEditor()
        assertEquals("수정 제출본 제목", editor.content.title)
        assertEquals("", editor.content.oneLineIntro)
        assertEquals("제출한 규칙", editor.content.storySettings.ruleSetting)
        assertEquals(
            "",
            editor.content.startSettings
                .single()
                .prologue,
        )
        assertEquals("pending-cover", editor.content.thumbnailImage?.objectKey)
        assertEquals("update-submission", editor.submission?.id)
    }

    @Test
    fun `일회 제출 본문은 원래 바이트와 헤더 값을 유지한다`() {
        val bytes = byteArrayOf(1, 2, 3)
        val body = SingleSubmissionBody(bytes.toRequestBody("application/json".toMediaType()))
        val sink = Buffer()
        body.writeTo(sink)
        assertTrue(body.isOneShot())
        assertEquals(bytes.size.toLong(), body.contentLength())
        assertEquals("application/json", body.contentType().toString())
        assertTrue(bytes.contentEquals(sink.readByteArray()))
        val upload = bytes.toRequestBody("image/png".toMediaType())
        assertEquals("image/png", upload.contentType().toString())
        assertEquals(bytes.size.toLong(), upload.contentLength())
    }

    @Test
    fun `반려 제출본의 이미지 키와 필드 사유를 폼 복원에 보존한다`() {
        val editor = json.decodeFromString<GeneralSubmissionDto>(submission).toEditor()
        assertEquals("sub-1", editor.submission?.id)
        assertEquals(true, editor.submission?.canResubmit)
        assertEquals("thumbnails/uploaded/drafts/owner/image.png", editor.content.thumbnailImage?.objectKey)
        assertEquals(
            "characters/uploaded/drafts/owner/image.png",
            editor.content.characters
                .single()
                .images
                .single()
                .objectKey,
        )
        assertEquals(
            "title",
            editor.submission
                ?.issues
                ?.single()
                ?.path,
        )
        assertEquals(
            "thumbnailUrl",
            editor.submission
                ?.imageErrors
                ?.single()
                ?.path,
        )
        assertNull(
            editor.content.characters
                .single()
                .id,
        )
    }

    @Test
    fun `파일 확장자와 관계없이 허용된 실제 이미지 헤더만 받는다`() {
        assertEquals(
            "image/png",
            imageContentType(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)),
        )
        assertEquals("image/jpeg", imageContentType(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())))
        assertEquals("image/webp", imageContentType("RIFF0000WEBP".toByteArray()))
        assertNull(imageContentType("not an image".toByteArray()))
        assertFalse(imageContentType("GIF89a".toByteArray()) != null)
    }
}

private val submission =
    """
    {
      "submissionId":"sub-1", "storyId":null, "kind":"CREATE", "status":"REJECTED",
      "payload":{
        "title":"검수 초안", "oneLineIntro":"한 줄", "visibility":"PRIVATE", "storySettings":{},
        "thumbnailObjectKey":"thumbnails/uploaded/drafts/owner/image.png", "thumbnailUrl":"https://images/cover.png",
        "characters":[{"id":null,"name":"가온","images":[{
          "id":null,"objectKey":"characters/uploaded/drafts/owner/image.png",
          "imageName":"가온_기본","imageUrl":"https://images/character.png"
        }]}]
      },
      "issues":[{"path":"title","type":"TEXT","rule":"policy","reason":"확인 필요"}],
      "imageErrors":[{"path":"thumbnailUrl","errorCode":"IMAGE_INVALID"}], "futureField":true
    }
    """.trimIndent()
