@file:Suppress("MagicNumber")

package app.manyak.create.general.domain

import app.manyak.create.general.entity.GeneralCharacter
import app.manyak.create.general.entity.GeneralGender
import app.manyak.create.general.entity.GeneralStoryForm
import app.manyak.create.general.entity.GeneralStorySettings
import kotlin.math.roundToInt

internal data class ParsedStorySettings(
    val world: String,
    val progression: String,
    val descriptionRatio: Int,
)

internal fun buildStorySettings(form: GeneralStoryForm) =
    GeneralStorySettings(
        worldSetting = joinSections(listOf("세계관" to form.world)),
        ruleSetting =
            joinSections(
                listOf(
                    "전개 규칙" to form.progression,
                    "분량 배분" to "묘사 ${form.descriptionRatio} : 대사 ${10 - form.descriptionRatio}",
                ),
            ),
        userRoleSetting = buildProtagonistSetting(form.protagonist),
        characterSetting =
            "# 등장인물\n\n" +
                form.supporting.joinToString("\n\n") { character ->
                    listOfNotNull(
                        "## ${character.name.trim()}",
                        character.gender?.let { "### 성별\n${it.asSettingText()}" },
                        character.feature.trim().takeIf(String::isNotEmpty),
                    ).joinToString("\n")
                },
    )

/** 이름은 `protagonistName` 으로 따로 보낸다. 글에 넣으면 서버가 `{username}` 을 쓸 이름이 없다고 거절한다. */
private fun buildProtagonistSetting(character: GeneralCharacter) =
    listOfNotNull(
        "# 주인공",
        character.gender?.let { "## 성별\n${it.asSettingText()}" },
        character.feature.trim().takeIf(String::isNotEmpty),
    ).joinToString("\n")

private fun GeneralGender.asSettingText() =
    when (this) {
        GeneralGender.MALE -> "남성"
        GeneralGender.FEMALE -> "여성"
    }

private fun joinSections(sections: List<Pair<String, String>>) =
    sections.filter { it.second.isNotBlank() }.joinToString("\n\n") { (heading, body) -> "# $heading\n${body.trim()}" }

internal fun parseStorySettings(settings: GeneralStorySettings): ParsedStorySettings {
    val world = splitSections(settings.worldSetting, listOf("세계관")).first()
    val rule = splitSections(settings.ruleSetting, listOf("전개 규칙", "분량 배분"))
    val ratio = Regex("묘사\\s*(\\d+)\\s*:\\s*대사\\s*(\\d+)").find(rule[1])
    val description = ratio?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    val dialogue = ratio?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
    val parsedRatio =
        if (description + dialogue > 0) {
            ((description / (description + dialogue)) * 10).roundToInt().coerceIn(1, 9)
        } else {
            5
        }
    val remaining = if (ratio == null) rule[1] else rule[1].removeRange(ratio.range).trim()
    return ParsedStorySettings(
        world = world,
        progression = listOf(rule[0], remaining).filter(String::isNotBlank).joinToString("\n\n"),
        descriptionRatio = parsedRatio,
    )
}

private fun splitSections(
    text: String,
    headings: List<String>,
): List<String> {
    val bodies = headings.map { mutableListOf<String>() }
    var current = 0
    var nextAllowed = 0
    text.lines().forEach { line ->
        val found = headings.indices.firstOrNull { it >= nextAllowed && line.trim() == "# ${headings[it]}" }
        if (found == null) {
            bodies[current].add(line)
        } else {
            current = found
            nextAllowed = found + 1
        }
    }
    return bodies.map { it.joinToString("\n").trim() }
}

/**
 * 주인공 글에서 성별 절만 칸으로 옮긴다. 이름은 `protagonistName` 으로 따로 오므로, 예전 글 맨 앞의 호칭 절은
 * 칸으로 옮기지 않고 특징 본문 맨 앞에 그대로 남긴다.
 */
internal fun parseProtagonist(text: String): GeneralCharacter {
    val lines = dropLeadingHeading(text.lines(), "# 주인공")
    val (_, afterHonorific) = takeLeadingSection(lines, "## 호칭")
    val honorific = lines.dropLast(afterHonorific.size)
    val (gender, rest) = takeGender(afterHonorific, "## 성별")
    return GeneralCharacter(gender = gender, feature = (honorific + rest).joinToString("\n").trim())
}

internal fun parseSupporting(text: String): List<GeneralCharacter> {
    val lines = dropLeadingHeading(text.lines(), "# 등장인물")
    val blocks = mutableListOf<Pair<String, MutableList<String>>>()
    val preamble = mutableListOf<String>()
    lines.forEach { line ->
        val heading = Regex("^## (?!#)(.*)$").matchEntire(line.trim())
        if (heading == null) {
            (blocks.lastOrNull()?.second ?: preamble).add(line)
        } else {
            blocks.add(heading.groupValues[1].trim() to mutableListOf())
        }
    }
    if (blocks.isEmpty()) {
        return preamble
            .joinToString("\n")
            .trim()
            .takeIf(String::isNotEmpty)
            ?.let { listOf(GeneralCharacter(feature = it)) }
            .orEmpty()
    }
    return blocks.mapIndexed { index, (name, body) ->
        val (gender, rest) = takeGender(body, "### 성별")
        val feature = if (index == 0) preamble + "" + rest else rest
        GeneralCharacter(name = name, gender = gender, feature = feature.joinToString("\n").trim())
    }
}

private fun dropLeadingHeading(
    lines: List<String>,
    heading: String,
): List<String> {
    val first = lines.indexOfFirst(String::isNotBlank)
    return if (first >= 0 && lines[first].trim() == heading) lines.drop(first + 1) else lines
}

private fun takeLeadingSection(
    lines: List<String>,
    heading: String,
): Pair<String?, List<String>> {
    val first = lines.indexOfFirst(String::isNotBlank)
    return if (first >= 0 && lines[first].trim() == heading && first + 1 < lines.size) {
        lines[first + 1] to lines.drop(first + 2)
    } else {
        null to lines
    }
}

private fun takeGender(
    lines: List<String>,
    heading: String,
): Pair<GeneralGender?, List<String>> {
    val (value, rest) = takeLeadingSection(lines, heading)
    val gender =
        when (value?.trim()) {
            "남성" -> GeneralGender.MALE
            "여성" -> GeneralGender.FEMALE
            else -> null
        }
    return if (gender == null) null to lines else gender to rest
}
