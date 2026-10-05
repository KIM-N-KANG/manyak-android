@file:Suppress("MagicNumber")

package app.manyak.create.general.entity

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class GeneralStoryForm(
    val title: String = "",
    val oneLineIntro: String = "",
    val world: String = "",
    val progression: String = "",
    val descriptionRatio: Int = 5,
    val protagonist: GeneralCharacter = GeneralCharacter(),
    val supporting: List<GeneralCharacter> = listOf(GeneralCharacter()),
    val startSettings: List<GeneralStartSetting> = listOf(GeneralStartSetting()),
    val mainEvents: List<GeneralMainEvent> = emptyList(),
    val genres: List<String> = emptyList(),
    val description: String = "",
    val visibility: String = "PRIVATE",
    val cover: GeneralStoryImage? = null,
) {
    val hasInput: Boolean
        get() =
            listOf(title, oneLineIntro, world, progression, description).any(String::isNotBlank) ||
                descriptionRatio != 5 ||
                protagonist.hasInput ||
                supporting.any(GeneralCharacter::hasInput) ||
                startSettings.any(GeneralStartSetting::hasInput) ||
                mainEvents.isNotEmpty() ||
                genres.isNotEmpty() ||
                visibility != "PRIVATE" ||
                cover != null
}

@Serializable
enum class GeneralGender { MALE, FEMALE }

@Serializable
data class GeneralStoryImage(
    val objectKey: String? = null,
    val previewUrl: String = "",
    val localPath: String? = null,
    val serverId: String? = null,
)

@Serializable
data class GeneralCharacter(
    val id: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val name: String = "",
    val gender: GeneralGender? = null,
    val feature: String = "",
    val description: String = "",
    val image: GeneralStoryImage? = null,
) {
    val hasInput: Boolean
        get() = name.isNotBlank() || gender != null || feature.isNotBlank() || description.isNotBlank() || image != null
}

@Serializable
data class GeneralStartSetting(
    val id: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val name: String = "",
    val prologue: String = "",
    val situation: String = "",
    val suggestedInputs: List<String> = listOf("", "", ""),
    val endings: List<GeneralEnding> = emptyList(),
) {
    val hasInput: Boolean
        get() =
            listOf(name, prologue, situation).any(String::isNotBlank) ||
                suggestedInputs.any(String::isNotBlank) ||
                endings.isNotEmpty()
}

@Serializable
data class GeneralEnding(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val minTurns: String = "0",
    val condition: String = "",
    val epilogue: String = "",
) {
    val hasInput: Boolean
        get() = listOf(name, condition, epilogue).any(String::isNotBlank) || minTurns !in listOf("", "0")
}

@Serializable
data class GeneralMainEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val keySentence: String = "",
) {
    val hasInput: Boolean
        get() = listOf(name, description, keySentence).any(String::isNotBlank)
}
