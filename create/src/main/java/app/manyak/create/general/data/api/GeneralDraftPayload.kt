package app.manyak.create.general.data.api

import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 서버는 payload를 해석하지 않아 웹과 같은 v1 형식으로 주고받는다. 형식의 정본은 웹의 일반 제작 스냅숏이고,
 * 이미지는 객체 키만, 장르는 고른 순서의 키 목록과 직접 이름(`customTags`)으로 둔다.
 */
internal const val GENERAL_DRAFT_SCHEMA_VERSION = 1

internal fun GeneralStoryForm.toDraftRequest(baseVersion: Int?): PutStoryDraftDto =
    PutStoryDraftDto(
        mode = GENERAL_MODE,
        stage = "GENERAL_DRAFT",
        schemaVersion = GENERAL_DRAFT_SCHEMA_VERSION,
        payload = toDraftPayload(),
        baseVersion = baseVersion,
        title = title.trim().ifEmpty { null },
        oneLineIntro = oneLineIntro.trim().ifEmpty { null },
        thumbnailObjectKey = cover?.objectKey,
    )

internal fun GeneralStoryForm.toDraftPayload(): JsonObject =
    buildJsonObject {
        putJsonObject("texts") {
            put("title", title)
            put("oneLineIntro", oneLineIntro)
            put("world", world)
            put("progression", progression)
        }
        put("cover", cover.toPayloadImage())
        put("descriptionRatio", descriptionRatio)
        putJsonObject("protagonist") {
            put("name", protagonist.name)
            put("gender", protagonist.gender?.name)
            put("feature", protagonist.feature)
        }
        putJsonArray("supporting") {
            supporting.forEach {
                addJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("gender", it.gender?.name)
                    put("feature", it.feature)
                    put("description", it.description)
                    put("image", it.image.toPayloadImage())
                }
            }
        }
        putJsonArray("startSettings") { startSettings.forEach { add(it.toPayload()) } }
        putJsonArray("mainEvents") {
            mainEvents.forEach {
                addJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("description", it.description)
                    put("keySentence", it.keySentence)
                }
            }
        }
        // 폼은 장르를 이름으로만 들고 있어 모두 직접 이름으로 보낸다. 웹은 이름으로 제공 장르를 다시 찾는다.
        putJsonObject("genres") {
            putJsonArray("selected") {
                genres.forEach {
                    addJsonObject {
                        put("kind", "custom")
                        put("id", it)
                    }
                }
            }
            putJsonArray("customTags") {
                genres.forEach {
                    addJsonObject {
                        put("id", it)
                        put("name", it)
                    }
                }
            }
        }
        put("description", description)
        put("visibility", visibility)
    }

private fun GeneralStartSetting.toPayload(): JsonObject =
    buildJsonObject {
        put("id", id)
        put("name", name)
        put("prologue", prologue)
        put("situation", situation)
        putJsonArray("suggestedInputs") { suggestedInputs.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("endings") {
            endings.forEach {
                addJsonObject {
                    put("id", it.id)
                    put("name", it.name)
                    put("minTurns", it.minTurns)
                    put("condition", it.condition)
                    put("epilogue", it.epilogue)
                }
            }
        }
    }

private fun GeneralStoryImage?.toPayloadImage(): JsonElement =
    this?.objectKey?.let { key -> buildJsonObject { put("objectKey", key) } } ?: JsonNull

internal const val GENERAL_MODE = "GENERAL"
