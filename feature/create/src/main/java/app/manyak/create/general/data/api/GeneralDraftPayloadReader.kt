package app.manyak.create.general.data.api

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralEnding
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralMainEvent
import app.manyak.create.general.entity.GeneralStartSetting
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStoryImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** 제공 장르 id로 고른 장르가 있는지. 있으면 이름을 찾으려고 카탈로그가 필요하다. */
internal fun JsonObject.draftGenreTagIds(): List<Long> =
    genreKeys().mapNotNull { key -> key.takeIf { it.text("kind") == "tag" }?.let { it.long("id") } }

/**
 * payload를 폼으로 읽는다. 빠졌거나 타입이 다른 최상위 필드와 항목 안의 필드는 새 폼의 기본값으로 채운다.
 *
 * @param imageUrls 상세 응답의 객체 키별 미리보기 URL
 * @param genreNames 제공 장르 id별 이름. 목록에 없는 id의 장르는 뺀다
 */
internal fun JsonObject.toGeneralStoryForm(
    imageUrls: Map<String, String>,
    genreNames: Map<Long, String>,
): GeneralStoryForm {
    val empty = GeneralStoryForm()
    val texts = this["texts"] as? JsonObject
    val protagonist = this["protagonist"] as? JsonObject

    fun image(value: JsonElement?) =
        (value as? JsonObject)?.text("objectKey")?.let { GeneralStoryImage(it, imageUrls[it].orEmpty()) }
    return GeneralStoryForm(
        title = texts.text("title").orEmpty(),
        oneLineIntro = texts.text("oneLineIntro").orEmpty(),
        world = texts.text("world").orEmpty(),
        progression = texts.text("progression").orEmpty(),
        descriptionRatio = (this["descriptionRatio"] as? JsonPrimitive)?.intOrNull ?: empty.descriptionRatio,
        protagonist =
            GeneralCharacter(
                name = protagonist.text("name").orEmpty(),
                gender = protagonist.gender(),
                feature = protagonist.text("feature").orEmpty(),
            ),
        supporting =
            filledItems("supporting")?.map {
                val base = GeneralCharacter()
                GeneralCharacter(
                    id = it.text("id") ?: base.id,
                    name = it.text("name").orEmpty(),
                    gender = it.gender(),
                    feature = it.text("feature").orEmpty(),
                    description = it.text("description").orEmpty(),
                    image = image(it?.get("image")),
                )
            } ?: empty.supporting,
        startSettings = filledItems("startSettings")?.map { it.toStartSetting() } ?: empty.startSettings,
        mainEvents =
            items("mainEvents")?.map {
                val base = GeneralMainEvent()
                GeneralMainEvent(
                    id = it.text("id") ?: base.id,
                    name = it.text("name").orEmpty(),
                    description = it.text("description").orEmpty(),
                    keySentence = it.text("keySentence").orEmpty(),
                )
            } ?: empty.mainEvents,
        genres = readGenres(genreNames),
        description = text("description").orEmpty(),
        visibility = if (text("visibility") == "PUBLIC") "PUBLIC" else "PRIVATE",
        cover = image(this["cover"]),
    )
}

private fun JsonObject?.toStartSetting(): GeneralStartSetting {
    val base = GeneralStartSetting()
    return GeneralStartSetting(
        id = text("id") ?: base.id,
        name = text("name").orEmpty(),
        prologue = text("prologue").orEmpty(),
        situation = text("situation").orEmpty(),
        suggestedInputs =
            (this?.get("suggestedInputs") as? JsonArray)?.map { (it as? JsonPrimitive)?.textOrNull().orEmpty() }
                ?: base.suggestedInputs,
        endings =
            this.items("endings")?.map {
                val ending = GeneralEnding()
                GeneralEnding(
                    id = it.text("id") ?: ending.id,
                    name = it.text("name").orEmpty(),
                    minTurns = it.text("minTurns").orEmpty(),
                    condition = it.text("condition").orEmpty(),
                    epilogue = it.text("epilogue").orEmpty(),
                )
            } ?: base.endings,
    )
}

private fun JsonObject.readGenres(genreNames: Map<Long, String>): List<String> {
    val custom =
        ((this["genres"] as? JsonObject)?.get("customTags") as? JsonArray)
            .orEmpty()
            .mapNotNull { tag -> (tag as? JsonObject)?.let { it.text("id") to it.text("name") } }
            .toMap()
    return genreKeys()
        .mapNotNull { key ->
            when (key.text("kind")) {
                "tag" -> key.long("id")?.let(genreNames::get)
                "custom" -> key.text("id")?.let(custom::get)
                else -> null
            }
        }.distinct()
}

private fun JsonObject.genreKeys(): List<JsonObject> =
    ((this["genres"] as? JsonObject)?.get("selected") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

/** 배열이 아니면 null. 객체가 아닌 항목은 빈 항목(null)으로 둔다. */
private fun JsonObject?.items(key: String): List<JsonObject?>? =
    (this?.get(key) as? JsonArray)?.map { it as? JsonObject }

private fun JsonObject.filledItems(key: String): List<JsonObject?>? = items(key)?.takeIf { it.isNotEmpty() }

private fun JsonObject?.text(key: String): String? = (this?.get(key) as? JsonPrimitive)?.textOrNull()

private fun JsonPrimitive.textOrNull(): String? = content.takeIf { isString }

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

private fun JsonObject?.gender(): GeneralGender? = GeneralGender.entries.firstOrNull { it.name == text("gender") }
